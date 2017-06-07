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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.servicecontrol.v1.AllocateQuotaRequest;
import com.google.api.servicecontrol.v1.AllocateQuotaResponse;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.QuotaOperation;
import com.google.common.hash.HashCode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link QuotaRequestAggregator}.
 */
@RunWith(JUnit4.class)
public class QuotaRequestAggregatorTest {
  private static final String TEST_OPERATION_NAME = "aTestOperation";
  private static final String TEST_CONSUMER_ID = "testConsumerId";
  private static final String DEFAULT_NAME = "service.default";
  private static final String NO_CACHE_NAME = "service.no.cache";
  private static final String TEST_METRIC_NAME1 = "testMetricName1";
  private static final String TEST_METRIC_NAME2 = "testMetricName2";
  private static final AllocateQuotaRequest DEFAULT_REQUEST = AllocateQuotaRequest.newBuilder()
      .setAllocateOperation(QuotaOperation.newBuilder()
          .setMethodName(TEST_OPERATION_NAME)
          .setConsumerId(TEST_CONSUMER_ID)
          .addQuotaMetrics(MetricValueSet.newBuilder()
              .setMetricName(TEST_METRIC_NAME1))
          .addQuotaMetrics(MetricValueSet.newBuilder()
              .setMetricName(TEST_METRIC_NAME2)))
      .setServiceName(DEFAULT_NAME)
      .build();
  private static final AllocateQuotaResponse DEFAULT_RESPONSE =
      AllocateQuotaResponse.getDefaultInstance();
  private QuotaRequestAggregator NO_CACHE;
  private QuotaRequestAggregator DEFAULT;
  private FakeTicker ticker;
  private QuotaAggregationOptions options;

  @Before
  public void setUp() {
    this.ticker = new FakeTicker();
    NO_CACHE = new QuotaRequestAggregator(NO_CACHE_NAME,
        new QuotaAggregationOptions(-1 /* disables cache */, 2, 1), ticker);
    options = new QuotaAggregationOptions();
    DEFAULT = new QuotaRequestAggregator(DEFAULT_NAME, options, ticker);
  }

  @Test
  public void cacheDisabled() {
    assertThat(NO_CACHE.allocateQuota(
        DEFAULT_REQUEST.toBuilder().setServiceName(NO_CACHE_NAME).build())).isNull();
  }

  @Test
  public void uncachedResponse() {
    assertThat(DEFAULT.allocateQuota(DEFAULT_REQUEST))
        .isEqualTo(AllocateQuotaResponse.getDefaultInstance());
  }

  @Test
  public void cachedResponse() {
    DEFAULT.cacheResponse(DEFAULT_REQUEST, DEFAULT_RESPONSE);
    assertThat(DEFAULT.allocateQuota(DEFAULT_REQUEST)).isEqualTo(DEFAULT_RESPONSE);
  }

  @Test
  public void flush() {
    DEFAULT.cacheResponse(DEFAULT_REQUEST, DEFAULT_RESPONSE);
    DEFAULT.allocateQuota(DEFAULT_REQUEST);
    ticker.tick(options.getRefreshMillis(), TimeUnit.MILLISECONDS);
    assertThat(DEFAULT.flush()).hasSize(1);
  }

  @Test
  public void sign_metricOrderDoesntMatter() {
    HashCode sign = QuotaRequestAggregator.sign(DEFAULT_REQUEST);
    QuotaOperation.Builder op = DEFAULT_REQUEST.getAllocateOperation().toBuilder();
    op.clearQuotaMetrics()
        .addQuotaMetrics(MetricValueSet.newBuilder()
            .setMetricName(TEST_METRIC_NAME2))
        .addQuotaMetrics(MetricValueSet.newBuilder()
            .setMetricName(TEST_METRIC_NAME1));
    AllocateQuotaRequest reversedRequest = DEFAULT_REQUEST.toBuilder()
        .setAllocateOperation(op)
        .build();
    assertThat(QuotaRequestAggregator.sign(reversedRequest)).isEqualTo(sign);
  }
}
