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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.control.aggregator.ReportAggregationOptions;
import com.google.common.cache.Cache;

/**
 * Tests for ReportAggregationOptions.
 */
@RunWith(JUnit4.class)
public class ReportAggregationOptionsTest {
  @Test
  public void defaultConstructorShouldSpecifyTheDefaultValues() {
    ReportAggregationOptions options = new ReportAggregationOptions();
    assertEquals(ReportAggregationOptions.DEFAULT_NUM_ENTRIES, options.getNumEntries());
    assertEquals(ReportAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS,
        options.getFlushCacheEntryIntervalMillis());
  }

  @Test
  public void shouldFailToCreateCacheWithANullOutputDeque() {
    try {
      ReportAggregationOptions options = new ReportAggregationOptions();
      options.createCache(null);
      fail("should have raised NullPointerException");
    } catch (NullPointerException e) {
      // expected
    }
  }

  @Test
  public void shouldFailToCreateACacheWithANullTicker() {
    try {
      ReportAggregationOptions options = new ReportAggregationOptions();
      options.createCache(testDeque(), null);
      fail("should have raised NullPointerException");
    } catch (NullPointerException e) {
      // expected
    }
  }

  @Test
  public void shouldNotCreateACacheUnlessMaxSizeIsPositive() {
    for (int i : new int[] {-1, 0, 1}) {
      ReportAggregationOptions options = new ReportAggregationOptions(i,
          ReportAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS);
      if (i > 0) {
        assertNotNull(options.createCache(testDeque()));
      } else {
        assertNull(options.createCache(testDeque()));
      }
    }
  }

  @Test
  public void shouldCreateACacheEvenIfFlushIntervalIsNotPositive() {
    for (int i : new int[] {-1, 0, 1}) {
      ReportAggregationOptions options =
          new ReportAggregationOptions(ReportAggregationOptions.DEFAULT_NUM_ENTRIES, i);
      assertNotNull(options.createCache(testDeque()));
    }
  }

  @Test
  public void shouldCreateACacheThatFlushesToTheOutputDeque() {
    ReportAggregationOptions options = new ReportAggregationOptions(1,
        ReportAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS);

    ConcurrentLinkedDeque<Long> deque = testDeque();
    Cache<String, Long> cache = options.createCache(deque);
    cache.put("one", 1L);
    assertEquals(cache.size(), 1);
    assertEquals(deque.size(), 0);
    cache.put("two", 2L);
    assertEquals(cache.size(), 1);
    assertEquals(deque.size(), 1);
    cache.put("three", 3L);
    assertEquals(cache.size(), 1);
    assertEquals(deque.size(), 2);
  }

  @Test
  public void shouldCreateACacheThatFlushesToTheOutputDequeAfterFlushInterval() {
    ReportAggregationOptions options =
        new ReportAggregationOptions(ReportAggregationOptions.DEFAULT_NUM_ENTRIES, 1);

    ConcurrentLinkedDeque<Long> deque = testDeque();
    FakeTicker ticker = new FakeTicker();
    Cache<String, Long> cache = options.createCache(deque, ticker);
    cache.put("one", 1L);
    assertEquals(1, cache.size());
    assertEquals(0, deque.size());
    ticker.tick(1 /* expires the entry */, TimeUnit.MILLISECONDS);
    cache.cleanUp();
    assertEquals(0, cache.size());
    assertEquals(1, deque.size());
  }

  private static ConcurrentLinkedDeque<Long> testDeque() {
    return new ConcurrentLinkedDeque<Long>();
  }
}
