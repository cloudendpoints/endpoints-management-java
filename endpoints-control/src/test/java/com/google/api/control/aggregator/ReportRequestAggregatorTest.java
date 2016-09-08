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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.api.servicecontrol.v1.ReportRequest.Builder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

/**
 * Tests the behavior in {@link ReportRequestAggregator}
 *
 */
@RunWith(JUnit4.class)
public class ReportRequestAggregatorTest {
  private static final String TEST_CONSUMER_ID = "testConsumerId";
  private static final String NO_CACHE_NAME = "service.no.cache";
  private static final String CACHING_NAME = "service.caching";
  private static final int TEST_FLUSH_INTERVAL = 1;
  private static final ReportRequestAggregator NO_CACHE = new ReportRequestAggregator(NO_CACHE_NAME,
      new ReportAggregationOptions(-1 /* disables cache */, 1));
  private FakeTicker ticker;

  @Before
  public void createTicker() {
    this.ticker = new FakeTicker();
  }

  @Test
  public void whenNonCachingShouldNotCacheResponse() {
    assertFalse(NO_CACHE.report(createTestRequest(NO_CACHE_NAME)));
  }

  @Test
  public void whenNonCachingShouldHaveEmptyFlush() {
    assertEquals(0, NO_CACHE.flush().length);
  }

  @Test
  public void whenNonCachingShouldHaveWellKnownFlushInterval() {
    assertEquals(ReportRequestAggregator.NON_CACHING, NO_CACHE.getFlushIntervalMillis());
  }

  @Test
  public void whenCachingShouldHaveConfiguredFlushInterval() {
    ReportRequestAggregator agg = cachingAggregator();
    assertEquals(TEST_FLUSH_INTERVAL, agg.getFlushIntervalMillis());
  }

  @Test
  public void whenCachingShouldNotCacheRequestsWithImportantOperations() {
    ReportRequestAggregator agg = cachingAggregator();
    assertFalse(agg.report(createTestRequest(CACHING_NAME, Operation.Importance.HIGH, 3, 0)));
  }

  @Test
  public void whenCachingShouldNotCacheRequestsWithUnimportantOperations() {
    ReportRequestAggregator agg = cachingAggregator();
    assertTrue(agg.report(createTestRequest(CACHING_NAME, Operation.Importance.LOW, 3, 0)));
  }

  @Test
  public void whenCachingShouldCacheRequestsAndBatchThemOnFlush() {
    ReportRequestAggregator agg = cachingAggregator();
    ReportRequest req1 = createTestRequest(CACHING_NAME, Operation.Importance.LOW, 3, 0);
    ReportRequest req2 = createTestRequest(CACHING_NAME, Operation.Importance.LOW, 3, 3);
    assertTrue(agg.report(req1));
    assertTrue(agg.report(req2));
    assertEquals(0 /* before flush */, agg.flush().length);
    ticker.tick(TEST_FLUSH_INTERVAL, TimeUnit.MILLISECONDS);
    ReportRequest[] flushed = agg.flush();
    assertEquals(1, flushed.length);
    assertEquals(6, flushed[0].getOperationsCount());
  }

  @Test
  public void whenCachingShouldAggregateOperations() {
    int n = 261; // arbitrary
    ReportRequestAggregator agg = cachingAggregator();

    // Add many requests, but with the same ops all the time
    for (int i = 0; i < n; i++) {
      ReportRequest req = createTestRequest(CACHING_NAME, Operation.Importance.LOW, 2, 0);
      assertTrue(agg.report(req));
    }
    assertEquals(0 /* before flush */, agg.flush().length);
    ticker.tick(TEST_FLUSH_INTERVAL, TimeUnit.MILLISECONDS);
    ReportRequest[] flushed = agg.flush();
    assertEquals(1, flushed.length);
    assertEquals(2, flushed[0].getOperationsCount());

    // flushing again immediately should result in 0 entries
    assertEquals(0, agg.flush().length);
  }

  @Test
  public void whenCachingMayClearAggregatedOperations() {
    int n = 337; // arbitrary
    ReportRequestAggregator agg = cachingAggregator();

    // Add many requests, but with the same ops all the time
    for (int i = 0; i < n; i++) {
      ReportRequest req = createTestRequest(CACHING_NAME, Operation.Importance.LOW, 2, 0);
      assertTrue(agg.report(req));
    }
    assertEquals(0 /* before flush */, agg.flush().length);
    agg.clear();
    ticker.tick(TEST_FLUSH_INTERVAL, TimeUnit.MILLISECONDS);
    ReportRequest[] flushed = agg.flush();

    // Because the cache was cleared, flush returns nothing
    assertEquals(0, flushed.length);
  }

  @Test
  public void whenCachingShouldFailForRequestsWithTheWrongServiceName() {
    ReportRequestAggregator agg = cachingAggregator();
    try {
      agg.report(createTestRequest(CACHING_NAME + ".extra", Operation.Importance.HIGH, 3, 0));
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  private ReportRequest createTestRequest(String serviceName, Operation.Importance imp, int numOps,
      int opStartIndex) {
    Operation.Builder ob =
        Operation.newBuilder().setConsumerId(TEST_CONSUMER_ID).setImportance(imp);
    Builder b = ReportRequest.newBuilder();
    for (int i = 0; i < numOps; i++) {
      b.addOperations(ob.setOperationName(String.format("testOp%d", opStartIndex + i)));
    }
    return b.setServiceName(serviceName).build();
  }

  private ReportRequest createTestRequest(String serviceName) {
    return createTestRequest(serviceName, Operation.Importance.LOW, 3, 0);
  }

  private ReportRequestAggregator cachingAggregator() {
    ReportAggregationOptions options = new ReportAggregationOptions(
        ReportAggregationOptions.DEFAULT_NUM_ENTRIES, TEST_FLUSH_INTERVAL);
    return new ReportRequestAggregator(CACHING_NAME, options, /* default MetricKinds */ null,
        ticker);
  }
}
