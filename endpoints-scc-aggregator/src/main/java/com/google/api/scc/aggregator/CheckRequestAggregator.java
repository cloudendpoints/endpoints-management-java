/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.api.scc.aggregator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 * Caches and aggregates {@link CheckRequest}s.
 */
public class CheckRequestAggregator {
  /**
   * The flush interval returned by {@link #getFlushIntervalMillis() } when an instance is
   * configured to be non-caching.
   */
  public static final int NON_CACHING = -1;

  private static final int NANOS_PER_MILLI = 1000000;
  private static final CheckRequest[] NO_REQUESTS = new CheckRequest[] {};
  private static final Logger log = Logger.getLogger(CheckRequestAggregator.class.getName());

  private final String serviceName;
  private final CheckAggregationOptions options;
  private final Map<String, MetricKind> kinds;
  private final ConcurrentLinkedDeque<CachedItem> out;
  private final Cache<String, CachedItem> cache;
  private final Ticker ticker;

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code CheckRequest}s are being aggregated
   * @param options configures this instance's caching behavior
   * @param kinds specifies the {@link MetricKind} for specific metric names
   * @param ticker the time source used to determine expiration. When not specified, this defaults
   *        to {@link Ticker#systemTicker()}
   */
  public CheckRequestAggregator(String serviceName, CheckAggregationOptions options,
      @Nullable Map<String, MetricKind> kinds, @Nullable Ticker ticker) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName),
        "service name cannot be empty");
    Preconditions.checkNotNull(options, "options must be non-null");
    this.out = new ConcurrentLinkedDeque<CachedItem>();
    this.ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.cache = options.createCache(out, this.ticker);
    this.serviceName = serviceName;
    this.options = options;
    this.kinds = kinds;
  }

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code CheckRequest}s are being aggregated
   * @param options configures this instances caching behavior
   * @param kinds specifies the {@link MetricKind} for specific metric names
   */
  public CheckRequestAggregator(String serviceName, CheckAggregationOptions options,
      @Nullable Map<String, MetricKind> kinds) {
    this(serviceName, options, kinds, Ticker.systemTicker());
  }

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code CheckRequest}s are being aggregated
   * @param options configures this instances caching behavior
   */
  public CheckRequestAggregator(String serviceName, CheckAggregationOptions options) {
    this(serviceName, options, null);
  }

  /**
   * @return the interval in milliseconds between calls to {@link #flush}
   */
  public int getFlushIntervalMillis() {
    if (cache == null) {
      return NON_CACHING;
    } else {
      return options.getExpirationMillis();
    }
  }

  /**
   * @return the service whose {@code CheckRequest}s are being aggregated
   */
  public String getServiceName() {
    return serviceName;
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
      cache.invalidateAll();
      out.clear();
    }
  }

  /**
   * Flushes this instance's cache.
   *
   * The instance's driver should call the this method every {@link #getFlushIntervalMillis()}
   * milliseconds, and send the results to the check service.
   *
   * @return CheckRequest[] containing the CheckRequests that were pending
   */
  public CheckRequest[] flush() {
    if (cache == null) {
      return NO_REQUESTS;
    }

    // Thread safety - the current thread cleans up the cache, which may add multiple cached
    // aggregated operations to the output deque.
    synchronized (cache) {
      cache.cleanUp();
      ArrayList<CheckRequest> reqs = Lists.newArrayList();
      for (CachedItem item : out) {
        CheckRequest req = item.extractRequest();
        if (req != null) {
          reqs.add(req);
        }
      }
      out.clear();
      return reqs.toArray(new CheckRequest[reqs.size()]);
    }
  }

  /**
   * Adds the response from sending {@code req} to this instances cache.
   *
   * @param req a {@link CheckRequest}
   * @param resp the response from sending {@link CheckResponse}
   */
  public void addResponse(CheckRequest req, CheckResponse resp) {
    if (cache == null) {
      return;
    }
    String signature = sign(req).toString();
    synchronized (cache) {
      long now = ticker.read();
      int quotaScale = 0; // WIP
      CachedItem item = cache.asMap().get(signature);
      if (item == null) {
        cache.put(signature, new CachedItem(resp, serviceName, now, quotaScale));
      } else {
        item.lastCheckTimestamp = now;
        item.response = resp;
        item.quotaScale = quotaScale;
        item.isFlushing = false;
        cache.put(signature, item);
      }
    }
  }

  /**
   * Determine if a cached response corresponds to {@code req}.
   *
   * Determine if there are cache hits for the request in this instance as follows:
   *
   * <strong>Not in the Cache</strong> If {@code req} is not in the cache, it returns {@code null},
   * to indicate that the caller should send the request.
   *
   * <strong>Cache Hit, the response has errors</strong> When a cached response has errors, it's
   * assumed that {@code req}, would fail as well, so the cached response is returned. However, the
   * first request after the check interval has elapsed should be sent to the server to refresh the
   * response - until its response is received, the subsequent reqs should still return the failed
   * response.
   *
   * <strong>Cache Hit, the response passed</strong> When the cached response has no errors, it's
   * assumed that the {@code req} would pass as well, so the response is return, with quota tracking
   * updated so that it matches that in req.
   *
   * @param req a request to be sent to the service control service
   * @return a {code CheckResponse} if an applicable one is cached by this instance, otherwise
   *         {@link null}
   */
  public @Nullable CheckResponse check(CheckRequest req) {
    if (cache == null) {
      return null;
    }
    Preconditions.checkArgument(req.getServiceName().equals(serviceName), "service name mismatch");
    Preconditions.checkNotNull(req.getOperation(), "expected check operation was not present");
    if (req.getOperation().getImportance() != Importance.LOW) {
      return null; // send the request now if importance is not LOW
    }
    String signature = sign(req).toString();
    synchronized (cache) {
      CachedItem item = cache.asMap().get(signature);
      if (item == null) {
        return null; // signal caller to send the response
      } else {
        return handleCachedResponse(req, item);
      }
    }
  }

  private boolean isCurrent(CachedItem item) {
    long age = ticker.read() - item.lastCheckTimestamp;
    return age < (options.getFlushCacheEntryIntervalMillis() * NANOS_PER_MILLI);
  }

  private CheckResponse handleCachedResponse(CheckRequest req, CachedItem item) {
    synchronized (cache) {
      if (item.response.getCheckErrorsCount() > 0) {
        if (isCurrent(item)) {
          return item.response;
        }

        // Not current
        item.lastCheckTimestamp = ticker.read();
        return null; // signal the caller to make a new check request
      } else {
        item.updateRequest(req, kinds);
        if (isCurrent(item)) {
          return item.response;
        }
        if (item.isFlushing) {
          log.warning("latest check request has not completed");
        }

        // Not current
        item.isFlushing = true;
        item.lastCheckTimestamp = ticker.read();
        return null; // signal the caller to make a new check request
      }
    }
  }

  /**
   * Obtains the {@hashCode} for the contents of {@code value}.
   *
   * @param value a {@code CheckRequest} to be signed
   * @return the {@code HashCode} corresponding to {@code value}
   */
  public static HashCode sign(CheckRequest value) {
    Hasher h = Hashing.md5().newHasher();
    Operation o = value.getOperation();
    if (o == null || Strings.isNullOrEmpty(o.getConsumerId())
        || Strings.isNullOrEmpty(o.getOperationName())) {
      throw new IllegalArgumentException("CheckRequest should have a valid operation");
    }
    h.putString(o.getConsumerId(), StandardCharsets.UTF_8);
    h.putChar('\0');
    h.putString(o.getOperationName(), StandardCharsets.UTF_8);
    h.putChar('\0');
    Signing.putLabels(h, o.getLabels());
    for (MetricValueSet mvSet : o.getMetricValueSetsList()) {
      h.putString(mvSet.getMetricName(), StandardCharsets.UTF_8);
      h.putChar('\0');
      for (MetricValue metricValue : mvSet.getMetricValuesList()) {
        MetricValues.putMetricValue(h, metricValue);
      }
    }
    return h.hash();
  }

  /**
   * CachedItem holds items cached along with a {@link CheckRequest}
   *
   * {@code CachedItem} is thread compatible
   */
  private static class CachedItem {
    boolean isFlushing;
    long lastCheckTimestamp;
    int quotaScale;
    CheckResponse response;
    private final String serviceName;

    private OperationAggregator aggregator;

    /**
     * @param response the cached {@code CheckResponse}
     * @param serviceName the name of the service whose request are being aggregated
     * @param lastCheckTimestamp the last time the {@code CheckRequest} for tracked by this item was
     *        checked
     * @param quotaScale WIP, used to track quota
     */
    CachedItem(CheckResponse response, String serviceName, long lastCheckTimestamp,
        int quotaScale) {
      this.response = response;
      this.serviceName = serviceName;
      this.lastCheckTimestamp = lastCheckTimestamp;
      this.quotaScale = quotaScale;
    }

    public String getServiceName() {
      return serviceName;
    }

    public void updateRequest(CheckRequest req, Map<String, MetricKind> kinds) {
      if (aggregator == null) {
        aggregator = new OperationAggregator(req.getOperation(), kinds);
      } else {
        aggregator.add(req.getOperation());
      }
    }

    public CheckRequest extractRequest() {
      if (aggregator == null) {
        return null;
      }
      Operation op = this.aggregator.asOperation();
      this.aggregator = null;
      return CheckRequest.newBuilder().setServiceName(this.serviceName).setOperation(op).build();
    }
  }
}
