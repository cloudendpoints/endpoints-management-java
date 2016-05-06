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
 * Holds values used to configure report aggregation.
 */
public class ReportAggregationOptions {
  /**
   * The default aggregation cache size.
   */
  public static final int DEFAULT_NUM_ENTRIES = 10000;

  /**
   * The default flush cache entry interval
   */
  public static final int DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS = 1000;

  private final int numEntries;
  private final int flushCacheEntryIntervalMillis;

  /**
   * Constructor
   *
   * @param numEntries is the maximum number of cache entries that can be kept in the aggregation
   *        cache. The cache is disabled if this value is negative.
   * @param flushCacheEntryIntervalMillis the maximum interval before aggregated report requests are
   *        flushed to the server. The cache entry is deleted after the flush
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
}
