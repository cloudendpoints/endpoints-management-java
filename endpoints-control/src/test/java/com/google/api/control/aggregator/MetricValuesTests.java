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
import static org.junit.Assert.fail;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.control.model.Distributions;
import com.google.api.servicecontrol.v1.Distribution;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValue.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.protobuf.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests {@link MetricValues}
 */
@RunWith(JUnit4.class)
public class MetricValuesTests {
  private static final double TOLERANCE = 1e-5;
  private static final double A_DOUBLE_VALUE = 0.1;
  private static final Timestamp EARLY = Timestamp.newBuilder().setNanos(1).setSeconds(100).build();
  private static final Timestamp LATER = Timestamp.newBuilder().setNanos(2).setSeconds(100).build();
  private static final Map<String, String> TEST_LABELS =
      ImmutableMap.<String, String>of("key1", "value1", "key2", "value2");

  private MetricValue testValue;
  private MetricValue otherValue;
  private MetricValue earlyEndingTestValue;
  private MetricValue laterEndingTestValue;

  @Before
  public void setUpMetricValue() {
    testValue =
        MetricValue.newBuilder().putAllLabels(TEST_LABELS).setDoubleValue(A_DOUBLE_VALUE).build();
    Builder fromTestValue = testValue.toBuilder();
    earlyEndingTestValue = fromTestValue.setEndTime(EARLY).build();
    laterEndingTestValue = fromTestValue.setEndTime(LATER).build();
    otherValue =
        MetricValue.newBuilder().setDoubleValue(A_DOUBLE_VALUE).putAllLabels(TEST_LABELS).build();
  }

  @Test
  public void putMetricValueShouldUpdateHashesConsistently() {
    HashFunction hf = Hashing.md5();
    Hasher hasher1 = hf.newHasher();
    Hasher hasher2 = hf.newHasher();
    HashCode hash1 = MetricValues.putMetricValue(hasher1, testValue).hash();
    HashCode hash2 = MetricValues.putMetricValue(hasher2, otherValue).hash();
    assertEquals(hash1, hash2);
  }

  @Test
  public void signShouldProduceConsistentHashCodes() {
    assertEquals(MetricValues.sign(testValue), MetricValues.sign(otherValue));
  }

  @Test
  public void mergeOfNonDeltaKindsShouldReturnMetricWithLatestEndtime() {
    MetricKind[] nonDeltas =
        new MetricKind[] {MetricKind.CUMULATIVE, MetricKind.GAUGE, MetricKind.UNRECOGNIZED};
    for (MetricKind kind : nonDeltas) {
      assertEquals(MetricValues.merge(kind, earlyEndingTestValue, laterEndingTestValue),
          laterEndingTestValue);
      assertEquals(MetricValues.merge(kind, laterEndingTestValue, earlyEndingTestValue),
          laterEndingTestValue);
    }
  }

  @Test
  public void mergeShouldFailForMetricsWithDifferentTypesOfValues() {
    MetricValue withChangedType = testValue.toBuilder().setInt64Value(1L).build();
    MetricKind[] testKinds =
        new MetricKind[] {MetricKind.CUMULATIVE, MetricKind.GAUGE, MetricKind.UNRECOGNIZED, MetricKind.DELTA};
    for (MetricKind kind : testKinds) {
      try {
        MetricValues.merge(kind, testValue, withChangedType);
        fail("Should have raised IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void mergeShouldFailForDeltaMetricsWithUnmergableTypes() {
    MetricValue withBooleanType = testValue.toBuilder().setBoolValue(true).build();
    MetricValue withStringType = testValue.toBuilder().setStringValue("test").build();
    MetricValue withNoneSet = testValue.toBuilder().clearDoubleValue().build();
    MetricValue[] unmergeables = new MetricValue[] {
      withBooleanType,
      withStringType,
      withNoneSet
    };
    for (MetricValue mv : unmergeables) {
      try {
        MetricValues.merge(MetricKind.DELTA, mv, mv);
        fail("Should have raised IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void mergeShouldSucceedForDeltaMetricsOfTheDoubleType() {
    MetricValue merged = MetricValues.merge(MetricKind.DELTA, testValue, testValue);
    assertEquals(merged.getDoubleValue(), 2 * testValue.getDoubleValue(), TOLERANCE);
  }

  @Test
  public void mergeShouldSucceedForDeltaMetricsOfTheInt64Type() {
    MetricValue withInt64 = testValue.toBuilder().setInt64Value(1L).build();
    MetricValue merged = MetricValues.merge(MetricKind.DELTA, withInt64, withInt64);
    assertEquals(merged.getInt64Value(), 2 * withInt64.getInt64Value());
  }

  @Test
  public void mergeShouldSucceedForDeltaMetricsOfTheDistributionType() {
    Distribution d = Distributions.createExplicit(new double[] {0.1, 0.3, 0.5});
    Distributions.addSample(0.4, d);
    MetricValue withDistribution = testValue.toBuilder().setDistributionValue(d).build();
    MetricValue merged = MetricValues.merge(MetricKind.DELTA, withDistribution, withDistribution);
    assertEquals(merged.getDistributionValue().getCount(), 2 * d.getCount());
  }
}
