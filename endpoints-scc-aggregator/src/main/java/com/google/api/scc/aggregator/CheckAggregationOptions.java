/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.scc.aggregator;

/**
 * Holds values used to configure check aggregation.
 */
public class CheckAggregationOptions {
  /**
   * The default aggregation cache size.
   */
  public static final int DEFAULT_NUM_ENTRIES = 10000;

  /**
   * The default response expiration interval.
   */
  public static final int DEFAULT_RESPONSE_EXPIRATION_MILLIS = 1000;

  /**
   * The default flush cache entry interval.
   */
  public static final int DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS = 500;

  private final int numEntries;
  private final int flushCacheEntryIntervalMillis;
  private final int expirationMillis;

  /**
   * Constructor
   *
   * @param numEntries is the maximum number of cache entries that can be kept in the aggregation
   *        cache. The cache is disabled if this value is negative.
   * @param flushCacheEntryIntervalMillis the maximum interval before an aggregated check request is
   *        flushed to the server. The cache entry is deleted after the flush
   * @param expirationMillis is the maximum interval in milliseconds before a cached check response
   *        is invalidated. This value should be greater than {@code flushCacheEntryIntervalMillis}.
   *        If not, it is ignored, and a value of {@code flushCacheEntryIntervalMillis} is used
   *        instead.
   */
  public CheckAggregationOptions(int numEntries, int flushCacheEntryIntervalMillis,
      int expirationMillis) {
    this.numEntries = numEntries;
    this.flushCacheEntryIntervalMillis = flushCacheEntryIntervalMillis;
    this.expirationMillis = Math.max(expirationMillis, flushCacheEntryIntervalMillis + 1);
  }

  /**
   * No-arg constructor
   *
   * Creates an instance initialized with the default values.
   */
  public CheckAggregationOptions() {
    this(DEFAULT_NUM_ENTRIES, DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS,
        DEFAULT_RESPONSE_EXPIRATION_MILLIS);
  }

  /**
   * @return the maximum number of cache entries that can be kept in the aggregation cache.
   */
  public int getNumEntries() {
    return numEntries;
  }

  /**
   * @return the maximum interval before aggregated report requests are flushed to the server
   */
  public int getFlushCacheEntryIntervalMillis() {
    return flushCacheEntryIntervalMillis;
  }

  /**
   * @return the maximum interval before a cached check response should be deleted. This value will
   *         not be greater than {@link #getFlushCacheEntryIntervalMillis()}
   */
  public int getExpirationMillis() {
    return expirationMillis;
  }
}
