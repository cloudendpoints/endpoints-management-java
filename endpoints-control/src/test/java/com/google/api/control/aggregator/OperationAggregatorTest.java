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

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.control.aggregator.OperationAggregator;
import com.google.api.servicecontrol.v1.LogEntry;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;

/**
 * Tests the behavior of {@link OperationAggregator}
 */
@RunWith(JUnit4.class)
public class OperationAggregatorTest {
  private static final double TEST_DOUBLE_VALUE = 1.1;
  private static final Timestamp EARLIEST =
      Timestamp.newBuilder().setNanos(1).setSeconds(1).build();
  private static final Timestamp EARLY = Timestamp.newBuilder().setNanos(1).setSeconds(100).build();
  private static final Timestamp LATER = Timestamp.newBuilder().setNanos(2).setSeconds(100).build();
  private static final Timestamp LATEST =
      Timestamp.newBuilder().setNanos(100).setSeconds(100).build();
  private static final AggregationTest[] AGGREGEATION_TESTS = new AggregationTest[] {
      new AggregationTest("update the start time to that of the earliest", null,
          newOperation(EARLY, LATER).build(),
          new Operation[] {
              newOperation(EARLIEST, LATER).build(),
              newOperation(LATER, LATER).build()},
          newOperation(EARLIEST, LATER).build()),
      new AggregationTest("update the end time to that of the latest", null,
          newOperation(EARLY, LATER).build(),
          new Operation[] {
              newOperation(EARLIEST, LATER).build(),
              newOperation(LATER, LATEST).build()},
          newOperation(EARLIEST, LATEST).build()),
      new AggregationTest("combine the log entries", null,
          newOperation(EARLY, LATER).addLogEntries(newLogEntry("initial")).build(),
          new Operation[] {
              newOperation(EARLY, LATER).addLogEntries(newLogEntry("agg1")).build(),
              newOperation(EARLY, LATER).addLogEntries(newLogEntry("agg2")).build()},
          newOperation(EARLY, LATER)
              .addLogEntries(newLogEntry("initial"))
              .addLogEntries(newLogEntry("agg1"))
              .addLogEntries(newLogEntry("agg2"))
              .build()),
      new AggregationTest("combines the metric value using the default kind", null,
          newOperation(EARLY, LATER)
              .addMetricValueSets(
                  newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE, EARLY))
              .addMetricValueSets(newDoubleMetricValueSet("some_doubles", TEST_DOUBLE_VALUE, EARLY))
              .build(),
          new Operation[] {
              newOperation(EARLY, LATER)
                  .addMetricValueSets(
                      newDoubleMetricValueSet("some_doubles", TEST_DOUBLE_VALUE, LATER))
                  .build(),
              newOperation(EARLY, LATER)
                  .addMetricValueSets(
                      newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE, LATEST))
                  .build()},
          newOperation(EARLY, LATER)
              .addMetricValueSets(
                  newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE * 2, LATEST))
              .addMetricValueSets(
                  newDoubleMetricValueSet("some_doubles", TEST_DOUBLE_VALUE * 2, LATER))
              .build()),
      new AggregationTest("combines the metric value using a kind other than DELTA",
          ImmutableMap.of("gauge_doubles",
              MetricKind.GAUGE),
          newOperation(EARLY, LATER)
              .addMetricValueSets(
                  newDoubleMetricValueSet("gauge_doubles", TEST_DOUBLE_VALUE, EARLY))
              .addMetricValueSets(
                  newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE, EARLY))
              .build(),
          new Operation[] {
              newOperation(EARLY, LATER)
                  .addMetricValueSets(
                      newDoubleMetricValueSet("gauge_doubles", TEST_DOUBLE_VALUE, LATER))
                  .build(),
              newOperation(EARLY, LATER)
                  .addMetricValueSets(
                      newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE, LATEST))
                  .build()},
          newOperation(EARLY, LATER)
              .addMetricValueSets(
                  newDoubleMetricValueSet("gauge_doubles", TEST_DOUBLE_VALUE, LATER))
              .addMetricValueSets(
                  newDoubleMetricValueSet("other_doubles", TEST_DOUBLE_VALUE * 2, LATEST))
              .build())};

  @Test
  public void testShouldAggregateAsExpected() {
    for (AggregationTest t : AGGREGEATION_TESTS) {
      OperationAggregator agg = new OperationAggregator(t.initial, t.kinds);
      for (Operation operation : t.ops) {
        agg.add(operation);
      }
      Operation got = agg.asOperation();
      assertEquals(t.description, t.want, got);
    }
  }

  private static MetricValueSet newDoubleMetricValueSet(String name, double value,
      Timestamp endTime) {
    return MetricValueSet
        .newBuilder()
        .setMetricName(name)
        .addMetricValues(MetricValue.newBuilder().setDoubleValue(value).setEndTime(endTime))
        .build();
  }

  private static LogEntry newLogEntry(String message) {
    return LogEntry.newBuilder().setName(message).build();
  }

  private static Operation.Builder newOperation(Timestamp start, Timestamp end) {
    return Operation.newBuilder().setStartTime(start).setEndTime(end);
  }

  private static final class AggregationTest {
    public String description;
    public Map<String, MetricKind> kinds;
    public Operation initial;
    public Operation[] ops;
    public Operation want;

    AggregationTest(String description, Map<String, MetricKind> kinds, Operation initial,
        Operation[] ops, Operation want) {
      this.description = description;
      this.kinds = kinds;
      this.initial = initial;
      this.ops = ops;
      this.want = want;
    }
  }
}
