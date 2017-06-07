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

import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.QuotaOperation;
import com.google.protobuf.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the behavior of {@link OperationAggregator}
 */
@RunWith(JUnit4.class)
public class QuotaOperationAggregatorTest {
  private static final String TEST_METRIC_NAME = "testMetric";
  private static final long TEST_LONG_VALUE = 1234;
  private static final Timestamp EARLIEST =
      Timestamp.newBuilder().setNanos(1).setSeconds(1).build();
  private static final Timestamp EARLY = Timestamp.newBuilder().setNanos(1).setSeconds(100).build();
  private static final Timestamp LATER = Timestamp.newBuilder().setNanos(2).setSeconds(100).build();
  private static final Timestamp LATEST =
      Timestamp.newBuilder().setNanos(100).setSeconds(100).build();

  @Test
  public void updateEarlierStartTime() {
    doTest(
        newOperation(EARLY, LATER).build(),
        newOperation(EARLIEST, LATER, TEST_LONG_VALUE * 3).build(),  // expected
        newOperation(EARLIEST, LATER).build(),
        newOperation(LATER, LATER).build());
  }

  @Test
  public void updateLaterEndTime() {
    doTest(
        newOperation(EARLY, LATER).build(),
        newOperation(EARLIEST, LATEST, TEST_LONG_VALUE * 3).build(),  // expected
        newOperation(EARLIEST, LATER).build(),
        newOperation(LATER, LATEST).build());
  }

  private void doTest(QuotaOperation initial, QuotaOperation expected, QuotaOperation... ops) {
    QuotaOperationAggregator agg = new QuotaOperationAggregator(initial);
    for (QuotaOperation op : ops) {
      agg.mergeOperation(op);
    }
    assertThat(agg.asQuotaOperation()).isEqualTo(expected);
  }

  private static MetricValueSet newLongMetricValueSet(String name, long value,
      Timestamp startTime, Timestamp endTime) {
    return MetricValueSet
        .newBuilder()
        .setMetricName(name)
        .addMetricValues(MetricValue.newBuilder()
            .setInt64Value(value)
            .setStartTime(startTime)
            .setEndTime(endTime))
        .build();
  }

  private static QuotaOperation.Builder newOperation(Timestamp start, Timestamp end) {
    return newOperation(start, end, TEST_LONG_VALUE);
  }

  private static QuotaOperation.Builder newOperation(Timestamp start, Timestamp end, long value) {
    return QuotaOperation.newBuilder()
        .addQuotaMetrics(newLongMetricValueSet(TEST_METRIC_NAME, value, start, end));
  }
}
