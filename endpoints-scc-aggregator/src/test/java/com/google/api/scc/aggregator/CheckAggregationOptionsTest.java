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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;

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

  static class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    /** Advances the ticker value by {@code time} in {@code timeUnit}. */
    public FakeTicker tick(long time, TimeUnit timeUnit) {
      nanos.addAndGet(timeUnit.toNanos(time));
      return this;
    }

    @Override
    public long read() {
      return nanos.getAndAdd(0);
    }
  }
}
