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

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * Holds values used to configure report aggregation.
 */
public class ReportAggregationOptions {
  /**
   * The default aggregation cache size.
   */
  public static final int DEFAULT_NUM_ENTRIES = 1000;

  /**
   * The default flush cache entry interval
   */
  public static final int DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS = 4000;

  private final int numEntries;
  private final int flushCacheEntryIntervalMillis;

  /**
   * Constructor
   *
   * @param numEntries
   *            is the maximum number of cache entries that can be kept in the
   *            aggregation cache. The cache is disabled if this value is
   *            negative.
   * @param flushCacheEntryIntervalMillis
   *            the maximum interval before aggregated report requests are
   *            flushed to the server. The cache entry is deleted after the
   *            flush
   */
  public ReportAggregationOptions(int numEntries, int flushCacheEntryIntervalMillis) {
    this.numEntries = numEntries;
    this.flushCacheEntryIntervalMillis = flushCacheEntryIntervalMillis;
  }

  /**
   * No-arg constructor
   *
   * Creates an instance initialized with the default values.
   */
  public ReportAggregationOptions() {
    this(DEFAULT_NUM_ENTRIES, DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS);
  }

  /**
   * @return the maximum number of cache entries that can be kept in the
   *         aggregation cache.
   */
  public int getNumEntries() {
    return numEntries;
  }

  /**
   * @return the maximum interval before aggregated report requests are
   *         flushed to the server
   */
  public int getFlushCacheEntryIntervalMillis() {
    return flushCacheEntryIntervalMillis;
  }

  /**
   * Creates a {@link Cache} configured by this instance.
   *
   * @param <T> the type of object cached
   *
   * @param out
   *            a concurrent {@code Deque} to which previously cached values
   *            are added as they expire
   * @return a {@link Cache} corresponding to this instance's values or
   *         {@code null} unless {@link #numEntries} is positive.
   */
  @Nullable
  public <T> Cache<String, T> createCache(ConcurrentLinkedDeque<T> out) {
    return createCache(out, Ticker.systemTicker());
  }

  /**
   * Creates a {@link Cache} configured by this instance.
   *
   * @param <T>
   *            the type of the value stored in the Cache
   * @param out
   *            a concurrent {@code Deque} to which cached values are added as
   *            they are removed from the cache
   * @param ticker
   *            the time source used to determine expiration
   * @return a {@link Cache} corresponding to this instance's values or
   *         {@code null} unless {@code #numEntries} is positive.
   */
  @Nullable
  public <T> Cache<String, T> createCache(final ConcurrentLinkedDeque<T> out, Ticker ticker) {
    Preconditions.checkNotNull(out, "The out deque cannot be null");
    Preconditions.checkNotNull(ticker, "The ticker cannot be null");
    if (numEntries <= 0) {
      return null;
    }
    final RemovalListener<String, T> listener = new RemovalListener<String, T>() {
      @Override
      public void onRemoval(RemovalNotification<String, T> notification) {
        out.addFirst(notification.getValue());
      }
    };
    CacheBuilder<String, T> b = CacheBuilder.newBuilder().maximumSize(numEntries).ticker(ticker)
        .removalListener(listener);
    if (flushCacheEntryIntervalMillis >= 0) {
      b.expireAfterWrite(flushCacheEntryIntervalMillis, TimeUnit.MILLISECONDS);
    }
    return b.build();
  }
}
