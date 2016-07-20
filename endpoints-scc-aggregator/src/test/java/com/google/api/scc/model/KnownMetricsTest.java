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

package com.google.api.scc.model;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.servicecontrol.v1.Distribution;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;

/**
 * KnownMetricsTest tests the behavior in {@code KnownMetrics}
 */
@RunWith(JUnit4.class)
public class KnownMetricsTest {
  @Test
  public void shouldBeSupported() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldBeSupported();
    }
  }

  @Test
  public void shouldBeMatchCorrectly() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldMatchCorrectly();
    }
  }

  @Test
  public void shouldUpdateOperationCorrectly() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldUpdateRequestInfo();
    }
  }

  private static final StructuredTest[] ALL_TESTS = {
      new StructuredTest(KnownMetrics.CONSUMER_REQUEST_SIZES, KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_REQUEST_SIZES, KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_REQUEST_SIZES,
          KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.CONSUMER_RESPONSE_SIZES, KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_RESPONSE_SIZES, KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_RESPONSE_SIZES,
          KnownMetrics.newSizeDistribution()),
      new StructuredTest(KnownMetrics.CONSUMER_TOTAL_LATENCIES, KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_TOTAL_LATENCIES, KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_TOTAL_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.CONSUMER_BACKEND_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BACKEND_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_BACKEND_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.CONSUMER_REQUEST_OVERHEAD_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_REQUEST_OVERHEAD_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_REQUEST_OVERHEAD_LATENCIES,
          KnownMetrics.newTimeDistribution()),
      new StructuredTest(KnownMetrics.CONSUMER_REQUEST_COUNT, MetricValueSet
          .newBuilder()
          .setMetricName(KnownMetrics.CONSUMER_REQUEST_COUNT.getName())
          .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
          .build()),
      new StructuredTest(KnownMetrics.PRODUCER_REQUEST_COUNT,
          MetricValueSet
              .newBuilder()
              .setMetricName(KnownMetrics.PRODUCER_REQUEST_COUNT.getName())
              .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
              .build()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_REQUEST_COUNT,
          MetricValueSet
              .newBuilder()
              .setMetricName(KnownMetrics.PRODUCER_BY_CONSUMER_REQUEST_COUNT.getName())
              .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
              .build()),
      new StructuredTest(KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT),
      new StructuredTest(new ReportRequestInfo().setResponseCode(400),
          KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT,
          MetricValueSet
              .newBuilder()
              .setMetricName(KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT.getName())
              .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
              .build()),
      new StructuredTest(KnownMetrics.PRODUCER_REQUEST_ERROR_COUNT),
      new StructuredTest(new ReportRequestInfo().setResponseCode(400),
          KnownMetrics.PRODUCER_REQUEST_ERROR_COUNT,
          MetricValueSet
              .newBuilder()
              .setMetricName(KnownMetrics.PRODUCER_REQUEST_ERROR_COUNT.getName())
              .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
              .build()),
      new StructuredTest(KnownMetrics.PRODUCER_BY_CONSUMER_ERROR_COUNT),
      new StructuredTest(new ReportRequestInfo().setResponseCode(400),
          KnownMetrics.PRODUCER_BY_CONSUMER_ERROR_COUNT,
          MetricValueSet
              .newBuilder()
              .setMetricName(KnownMetrics.PRODUCER_BY_CONSUMER_ERROR_COUNT.getName())
              .addMetricValues(MetricValue.newBuilder().setDoubleValue(1L))
              .build()),
      };

  static class StructuredTest {
    StructuredTest() {
      wantedSize = 7426L; // arbitrary
      given = new ReportRequestInfo()
          .setRequestSize(wantedSize)
          .setResponseSize(wantedSize)
          .setBackendTimeMillis(wantedSize)
          .setRequestTimeMillis(wantedSize)
          .setOverheadTimeMillis(wantedSize)
          .setResponseSize(wantedSize);
    }

    StructuredTest(KnownMetrics subject) {
      this();
      this.subject = subject;
    }

    StructuredTest(KnownMetrics subject, MetricValueSet wantedMetrics) {
      this(subject);
      this.wantedMetrics = wantedMetrics;
    }

    StructuredTest(KnownMetrics subject, Distribution baseDistribution) {
      this(subject);
      this.wantedDistribution = Distributions.addSample(wantedSize, baseDistribution);
    }

    StructuredTest(ReportRequestInfo given, KnownMetrics subject, MetricValueSet wantedMetrics) {
      this(subject, wantedMetrics);
      this.given = given;
    }

    void shouldBeSupported() {
      Assert.assertTrue(KnownMetrics.isSupported(matchingDescriptor().build()));
      Assert.assertFalse(KnownMetrics.isSupported(notMatched().build()));
    }

    void shouldMatchCorrectly() {
      Assert.assertTrue(subject.matches(matchingDescriptor().build()));
      Assert.assertFalse(subject.matches(notMatched().build()));
    }

    void shouldUpdateRequestInfo() {
      Operation.Builder givenOp = baseOperation();
      Operation wanted = wantedOperation().build();
      subject.performUpdate(given, givenOp);
      Assert.assertEquals(wanted, givenOp.build());
    }

    Operation.Builder baseOperation() {
      return Operation
          .newBuilder()
          .setConsumerId("project:project_id")
          .setOperationId("anOperationId")
          .setOperationName("anOperationName");
    }

    Operation.Builder wantedOperation() {
      Operation.Builder result = baseOperation();
      if (wantedMetrics != null) {
        result.addMetricValueSets(wantedMetrics);
      } else if (wantedDistribution != null) {
        result.addMetricValueSets(MetricValueSet
            .newBuilder()
            .setMetricName(subject.getName())
            .addMetricValues(MetricValue.newBuilder().setDistributionValue(wantedDistribution))
            .build());
      }
      return result;
    }

    MetricDescriptor.Builder matchingDescriptor() {
      return MetricDescriptor
          .newBuilder()
          .setName(subject.getName())
          .setMetricKind(subject.getKind())
          .setValueType(subject.getType());
    }

    MetricDescriptor.Builder notMatched() {
      return matchingDescriptor().setMetricKind(MetricKind.METRIC_KIND_UNSPECIFIED);
    }

    KnownMetrics subject;
    MetricValueSet wantedMetrics;
    Distribution wantedDistribution;
    long wantedSize;
    ReportRequestInfo given;
  }
}
