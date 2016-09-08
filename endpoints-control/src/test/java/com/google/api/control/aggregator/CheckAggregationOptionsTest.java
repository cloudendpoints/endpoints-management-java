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

import com.google.common.cache.Cache;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * Tests for CheckAggregationOptions.
 */
@RunWith(JUnit4.class)
public class CheckAggregationOptionsTest {

  @Test
  public void defaultConstructorShouldSpecifyTheDefaultValues() {
    CheckAggregationOptions options = new CheckAggregationOptions();
    assertEquals(CheckAggregationOptions.DEFAULT_NUM_ENTRIES, options.getNumEntries());
    assertEquals(CheckAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS,
        options.getFlushCacheEntryIntervalMillis());
    assertEquals(CheckAggregationOptions.DEFAULT_RESPONSE_EXPIRATION_MILLIS,
        options.getExpirationMillis());
  }

  @Test
  public void constructorShouldIgnoreLowExpirationMillis() {
    CheckAggregationOptions options =
        new CheckAggregationOptions(-1, 1, 0 /* this is low and will be ignored */);
    assertEquals(-1, options.getNumEntries());
    assertEquals(1, options.getFlushCacheEntryIntervalMillis());
    assertEquals(2 /* cache interval + 1 */, options.getExpirationMillis());
  }

  @Test
  public void shouldFailToCreateCacheWithANullOutputDeque() {
    try {
      CheckAggregationOptions options = new CheckAggregationOptions();
      options.createCache(null);
      fail("should have raised NullPointerException");
    } catch (NullPointerException e) {
      // expected
    }
  }

  @Test
  public void shouldFailToCreateACacheWithANullTicker() {
    try {
      CheckAggregationOptions options = new CheckAggregationOptions();
      options.createCache(testDeque(), null);
      fail("should have raised NullPointerException");
    } catch (NullPointerException e) {
      // expected
    }
  }

  @Test
  public void shouldNotCreateACacheUnlessMaxSizeIsPositive() {
    for (int i : new int[] {-1, 0, 1}) {
      CheckAggregationOptions options = new CheckAggregationOptions(i,
          CheckAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS,
          CheckAggregationOptions.DEFAULT_RESPONSE_EXPIRATION_MILLIS);
      if (i > 0) {
        assertNotNull(options.createCache(testDeque()));
      } else {
        assertNull(options.createCache(testDeque()));
      }
    }
  }

  @Test
  public void shouldCreateACacheEvenIfExpirationIsNotPositive() {
    for (int i : new int[] {-1, 0, 1}) {
      CheckAggregationOptions options =
          new CheckAggregationOptions(CheckAggregationOptions.DEFAULT_NUM_ENTRIES, i - 1, i);
      assertNotNull(options.createCache(testDeque()));
    }
  }

  @Test
  public void shouldCreateACacheThatFlushesToTheOutputDeque() {
    CheckAggregationOptions options = new CheckAggregationOptions(1,
        CheckAggregationOptions.DEFAULT_FLUSH_CACHE_ENTRY_INTERVAL_MILLIS,
        CheckAggregationOptions.DEFAULT_RESPONSE_EXPIRATION_MILLIS);

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
  public void shouldCreateACacheThatFlushesToTheOutputDequeAfterExpiration() {
    CheckAggregationOptions options =
        new CheckAggregationOptions(CheckAggregationOptions.DEFAULT_NUM_ENTRIES, 0, 1);

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
