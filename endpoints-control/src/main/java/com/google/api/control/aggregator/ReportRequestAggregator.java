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

package com.google.api.control.aggregator;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.annotation.Nullable;

/**
 * A container that aggregates service control {@link ReportRequest}s.
 *
 * Thread-safe.
 */
public class ReportRequestAggregator {
  /**
   * The flush interval returned by {@link #getFlushIntervalMillis() } when an instance is
   * configured to be non-caching.
   */
  public static final int NON_CACHING = -1;

  /**
   * The maximum number of operations to send in a report request.
   */
  public static final int MAX_OPERATION_COUNT = 1000;
  private static final ReportRequest[] NO_REQUESTS = new ReportRequest[] {};

  private final Cache<String, OperationAggregator> cache;
  private final Map<String, MetricKind> kinds;
  private final ConcurrentLinkedDeque<OperationAggregator> out;
  private final ReportAggregationOptions options;
  private final String serviceName;

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code ReportRequest}s are being aggregated
   * @param options configures this instance's caching behavior
   * @param kinds specifies the {@link MetricKind} for specific metric names
   * @param ticker the time source used to determine expiration. When not specified, this defaults
   *        to {@link Ticker#systemTicker()}
   */
  public ReportRequestAggregator(String serviceName, ReportAggregationOptions options,
      @Nullable Map<String, MetricKind> kinds, @Nullable Ticker ticker) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName),
        "service name cannot be empty");
    Preconditions.checkNotNull(options, "options must be non-null");
    this.kinds = kinds == null ? ImmutableMap.<String, MetricKind>of() : ImmutableMap.copyOf(kinds);
    this.serviceName = serviceName;
    this.out = new ConcurrentLinkedDeque<OperationAggregator>();
    this.cache = options.createCache(out, ticker == null ? Ticker.systemTicker() : ticker);
    this.options = options;
  }

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code ReportRequest}s are being aggregated
   * @param options configures this instances caching behavior
   * @param kinds specifies the {@link MetricKind} for specific metric names
   */
  public ReportRequestAggregator(String serviceName, ReportAggregationOptions options,
      @Nullable Map<String, MetricKind> kinds) {
    this(serviceName, options, kinds, Ticker.systemTicker());
  }

  /**
   * Constructor.
   *
   * @param serviceName the service whose {@code ReportRequest}s are being aggregated
   * @param options configures this instances caching behavior
   */
  public ReportRequestAggregator(String serviceName, ReportAggregationOptions options) {
    this(serviceName, options, ImmutableMap.<String, MetricKind>of());
  }

  /**
   * @return the interval in milliseconds between calls to {@link #flush}
   */
  public int getFlushIntervalMillis() {
    if (cache == null) {
      return NON_CACHING;
    } else {
      return options.getFlushCacheEntryIntervalMillis();
    }
  }

  /**
   * @return the service whose {@code ReportRequest}s are being aggregated
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * Clears this instances cache of aggregated operations.
   *
   * Is intended to be called by the driver before shutdown.
   *
   * @return the remaining aggregated {code ReportRequest}s
   */
  public ReportRequest[] clear() {
    if (cache == null) {
      return NO_REQUESTS;
    }
    synchronized (cache) {
      ReportRequest[] res = generatedFlushRequests(cache.asMap().values());
      cache.invalidateAll();
      out.clear();
      return res;
    }
  }

  /**
   * Flushes this instance's cache.
   *
   * The instance's driver should call the this method every {@link #getFlushIntervalMillis()}
   * milliseconds, and send the results to the report service.
   *
   * @return ReportRequest[] corresponding to the operations aggregated from calls to
   *         {@link #report}
   */
  public ReportRequest[] flush() {
    if (cache == null) {
      return NO_REQUESTS;
    }

    // Thread safety - the current thread cleans up the cache, which may add multiple cached
    // aggregated operations to the output deque.
    synchronized (cache) {
      cache.cleanUp();
      // Thread safety - the rest of the function deals with items in a ConcurrentLinkedDeque which
      // guarantees a consistent view in multi-threaded scenarios.
      ReportRequest[] res = generatedFlushRequests(out);
      out.clear();
      return res;
    }
  }

  /**
   * Adds a report request to this instance's cache.
   *
   * @param req a {@code ReportRequest} to cache in this instance.
   * @return {@code true} if {@code req} was cached successfully, otherwise {@code false}
   */
  public boolean report(ReportRequest req) {
    if (cache == null) {
      return false;
    }
    Preconditions.checkArgument(req.getServiceName().equals(serviceName),
        String.format("service name mismatch. Aggregator service '%s', request service '%s'",
          serviceName, req.getServiceName()));
    if (hasHighImportanceOperation(req)) {
      return false;
    }
    Map<String, Operation> bySignature = opsBySignature(req);

    // Concurrency: all threads wait while the current thread updates the cache.
    //
    // It's better for overall latency to have one thread complete cache update at a time than have
    // multiple threads interleave updates with the increased cpu cost due to increased context
    // switching.
    //
    // No i/o or computation is occurring, so the wait time should be relatively small and
    // depend on the number of waiting threads.
    synchronized (cache) {
      for (Map.Entry<String, Operation> entry : bySignature.entrySet()) {
        String signature = entry.getKey();
        OperationAggregator agg = cache.getIfPresent(signature);
        if (agg == null) {
          cache.put(signature, new OperationAggregator(entry.getValue(), kinds));
        } else {
          agg.add(entry.getValue());
        }
      }
    }
    return true;
  }

  protected ReportRequest[] generatedFlushRequests(Iterable<OperationAggregator> aggregators) {
    ArrayList<ReportRequest> reqs = Lists.newArrayList();
    ReportRequest.Builder current = ReportRequest.newBuilder().setServiceName(serviceName);
    for (OperationAggregator agg : aggregators) {
      if (current.getOperationsCount() == MAX_OPERATION_COUNT) {
        reqs.add(current.build());
        current.clearOperations();
      }
      current.addOperations(agg.asOperation());
    }
    if (current.getOperationsCount() > 0) {
      reqs.add(current.build());
    }
    return reqs.toArray(new ReportRequest[] {});
  }

  /**
   * Obtains the {@hashCode} for the contents of {@code value}.
   *
   * @param value a {@code Operation} to be signed
   * @return the {@code HashCode} corresponding to {@code value}
   */
  private static HashCode sign(Operation value) {
    Hasher h = Hashing.md5().newHasher();
    h.putString(value.getConsumerId(), StandardCharsets.UTF_8);
    h.putChar('\0');
    h.putString(value.getOperationName(), StandardCharsets.UTF_8);
    h.putChar('\0');
    return Signing.putLabels(h, value.getLabels()).hash();
  }

  private static Map<String, Operation> opsBySignature(ReportRequest req) {
    HashMap<String, Operation> result = Maps.newHashMap();
    for (Operation op : req.getOperationsList()) {
      result.put(sign(op).toString(), op);
    }
    return result;
  }

  private static boolean hasHighImportanceOperation(ReportRequest req) {
    for (Operation operation : req.getOperationsList()) {
      if (operation.getImportance() == Importance.HIGH) {
        return true;
      }
    }
    return false;
  }
}
