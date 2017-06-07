/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.control.aggregator;

import com.google.api.servicecontrol.v1.AllocateQuotaRequest;
import com.google.api.servicecontrol.v1.AllocateQuotaResponse;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.QuotaOperation;
import com.google.api.servicecontrol.v1.QuotaOperation.QuotaMode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Caches and aggregates {@link com.google.api.servicecontrol.v1.AllocateQuotaRequest}s.
 */

public class QuotaRequestAggregator {
  private static final Logger log = Logger.getLogger(QuotaRequestAggregator.class.getName());
  public static final int NON_CACHING = -1;
  private static final long NANOS_PER_MILLI = 1000000;
  private final ConcurrentLinkedDeque<AllocateQuotaRequest> out;
  private final String serviceName;
  private final Ticker ticker;
  private final Cache<String, CachedItem> cache;
  private final QuotaAggregationOptions options;
  private final long timeoutIntervalNs;
  private boolean inFlushAll;

  public QuotaRequestAggregator(String serviceName, QuotaAggregationOptions options,
      @Nullable Ticker ticker) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName),
        "service name cannot be empty");
    Preconditions.checkNotNull(options, "options must be non-null");
    this.out = new ConcurrentLinkedDeque<>();
    this.ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.serviceName = serviceName;
    this.options = options;
    this.cache = createCache(this.ticker);
    this.timeoutIntervalNs = options.getTimeoutMillis() * NANOS_PER_MILLI;
  }

  /**
   * @return the interval in milliseconds between calls to {@link #flush}
   */
  public int getFlushIntervalMillis() {
    if (cache == null) {
      return NON_CACHING;
    } else {
      return options.getRefreshMillis();
    }
  }

  public List<AllocateQuotaRequest> flush() {
    if (cache == null) {
      return ImmutableList.of();
    }

    synchronized (cache) {
      cache.cleanUp();
      for (Map.Entry<String, CachedItem> entry : cache.asMap().entrySet()) {
        CachedItem item = entry.getValue();
        if (!inFlushAll && !shouldDrop(item)) {
          if (!item.isInFlight && item.aggregator != null) {
            item.isInFlight = true;
            item.lastRefreshTimestamp = ticker.read();
            out.add(item.extractRequest());
          }
        }
      }
      List<AllocateQuotaRequest> reqs = new ArrayList<>(out);
      out.clear();
      return reqs;
    }
  }

  /**
   * Clears this instances cache of aggregated operations.
   *
   * Is intended to be called by the driver before shutdown.
   */
  public void clear() {
    if (cache == null) {
      return;
    }
    synchronized (cache) {
      inFlushAll = true;
      cache.invalidateAll();
      out.clear();
      inFlushAll = false;
    }
  }

  public AllocateQuotaResponse allocateQuota(AllocateQuotaRequest req) {
    Preconditions.checkArgument(req.getServiceName().equals(serviceName), "service name mismatch");
    Preconditions.checkArgument(
        req.hasAllocateOperation(), "expected quota operation was not present");

    if (cache == null) {
      // By returning NO_FOUND, caller will send request to server.
      return null;
    }

    String signature = sign(req).toString();
    synchronized (cache) {
      CachedItem item = cache.getIfPresent(signature);
      if (item == null) {
        // To avoid sending concurrent allocateQuota from concurrent requests.
        // insert a temporary positive response to the cache. Requests from other
        // requests will be aggregated to this temporary element until the
        // response for the actual request arrives.
        AllocateQuotaResponse tempResponse = AllocateQuotaResponse.getDefaultInstance();
        item = new CachedItem(req, tempResponse, ticker.read());
        item.signature = signature;
        item.isInFlight = true;
        cache.put(signature, item);

        // Triggers refresh
        out.add(req);

        // return positive response
        return tempResponse;
      }

      if (!item.isInFlight && shouldRefresh(item)) {
        // Update inFlight to avoid duplicated request
        item.isInFlight = true;
        item.lastRefreshTimestamp = ticker.read();

        AllocateQuotaRequest refreshRequest = item.extractRequest();
        if (!item.isPositiveResponse()) {
          // If the cached response is negative, then use NORMAL QuotaMode
          // instead of BEST_EFFORT
          QuotaOperation allocateOp = refreshRequest.getAllocateOperation();
          refreshRequest.toBuilder()
              .setAllocateOperation(allocateOp.toBuilder().setQuotaMode(QuotaMode.NORMAL))
              .build();
        }

        // Triggers refresh
        out.add(refreshRequest);
      }

      // Aggregate tokens if the cached response is positive
      if (item.isPositiveResponse()) {
        item.aggregate(req);
      }

      return item.response;
    }
  }

  private boolean shouldRefresh(CachedItem item) {
    return ticker.read() - item.lastRefreshTimestamp >= options.getRefreshMillis();
  }

  public void cacheResponse(AllocateQuotaRequest req, AllocateQuotaResponse resp) {
    if (cache == null) {
      return;
    }
    String signature = sign(req).toString();
    synchronized (cache) {
      CachedItem item = cache.getIfPresent(signature);
      if (item != null) {
        item.isInFlight = false;
        item.response = resp;
      }
    }
  }

  /**
   * CachedItem holds items cached along with a {@link AllocateQuotaRequest}
   *
   * {@code CachedItem} is thread safe
   */
  private static class CachedItem {
    private boolean isInFlight = false;
    private long lastRefreshTimestamp;
    private AllocateQuotaRequest request;
    private AllocateQuotaResponse response;
    private final String serviceName;
    private QuotaOperationAggregator aggregator;
    private String signature;

    CachedItem(AllocateQuotaRequest req, AllocateQuotaResponse resp, long lastRefreshTimestamp) {
      this.request = req;
      this.response = resp;
      this.lastRefreshTimestamp = lastRefreshTimestamp;
      this.serviceName = request.getServiceName();
    }

    synchronized void aggregate(AllocateQuotaRequest req) {
      if (aggregator == null) {
        aggregator = new QuotaOperationAggregator(req.getAllocateOperation());
      } else {
        aggregator.mergeOperation(req.getAllocateOperation());
      }
    }

    synchronized AllocateQuotaRequest extractRequest() {
      if (this.aggregator == null) {
        return this.request;
      }
      QuotaOperation op = this.aggregator.asQuotaOperation();
      this.aggregator = null;
      return AllocateQuotaRequest.newBuilder()
          .setServiceName(this.serviceName)
          .setAllocateOperation(op)
          .build();
    }

    synchronized void clearAllocationErrors() {
      response = response.toBuilder().clearAllocateErrors().build();
    }

    synchronized void setQuotaResponse(AllocateQuotaResponse response) {
      this.response = response;
      if (response.getAllocateErrorsCount() > 0) {
        aggregator = null;
      }
    }

    boolean isPositiveResponse() {
      return response.getAllocateErrorsCount() == 0;
    }
  }

  @VisibleForTesting
  static HashCode sign(AllocateQuotaRequest req) {
    Hasher h = Hashing.md5().newHasher();
    QuotaOperation o = req.getAllocateOperation();
    h.putString(o.getMethodName(), StandardCharsets.UTF_8);
    h.putChar('\0');
    h.putString(o.getConsumerId(), StandardCharsets.UTF_8);
    ImmutableSortedSet.Builder<String> builder =
        new ImmutableSortedSet.Builder<>(Ordering.natural());
    for (MetricValueSet mvSet : o.getQuotaMetricsList()) {
      builder.add(mvSet.getMetricName());
    }
    for (String metricName : builder.build()) {
      h.putChar('\0');
      h.putString(metricName, StandardCharsets.UTF_8);
    }
    return h.hash();
  }

  @Nullable
  private Cache<String, CachedItem> createCache(final Ticker ticker) {
    Preconditions.checkNotNull(ticker, "The ticker cannot be null");
    if (options.getNumEntries() <= 0) {
      return null;
    }
    CacheBuilder<Object, Object> b = CacheBuilder.newBuilder()
        .maximumSize(options.getNumEntries()).ticker(ticker);
    b.expireAfterWrite(options.getTimeoutMillis(), TimeUnit.MILLISECONDS);
    return b.build();
  }

  private boolean shouldDrop(CachedItem item) {
    long age = ticker.read() - item.lastRefreshTimestamp;
    return age >= timeoutIntervalNs;
  }
}
