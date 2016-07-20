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

import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.MetricDescriptor.ValueType;
import com.google.api.servicecontrol.v1.Distribution;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;

/**
 * KnownMetric enumerates the well-known metrics and allows them to be added to the ReportRequest.
 */
public enum KnownMetrics {
  CONSUMER_REQUEST_COUNT("serviceruntime.googleapis.com/api/consumer/request_count",
      MetricKind.DELTA, ValueType.INT64, add1ToInt64Metric()),

  PRODUCER_REQUEST_COUNT("serviceruntime.googleapis.com/api/producer/request_count",
      MetricKind.DELTA, ValueType.INT64, add1ToInt64Metric()),

  PRODUCER_BY_CONSUMER_REQUEST_COUNT(
      "serviceruntime.googleapis.com/api/producer/by_consumer/request_count", MetricKind.DELTA,
      ValueType.INT64, add1ToInt64Metric()),

  CONSUMER_REQUEST_SIZES("serviceruntime.googleapis.com/api/consumer/request_sizes",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForRequestSize()),

  PRODUCER_REQUEST_SIZES("serviceruntime.googleapis.com/api/producer/request_sizes",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForRequestSize()),

  PRODUCER_BY_CONSUMER_REQUEST_SIZES(
      "serviceruntime.googleapis.com/api/producer/by_consumer/request_sizes", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForRequestSize()),

  CONSUMER_RESPONSE_SIZES("serviceruntime.googleapis.com/api/consumer/response_sizes",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForResponseSize()),

  PRODUCER_RESPONSE_SIZES("serviceruntime.googleapis.com/api/producer/response_sizes",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForResponseSize()),

  PRODUCER_BY_CONSUMER_RESPONSE_SIZES(
      "serviceruntime.googleapis.com/api/producer/by_consumer/response_sizes", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForResponseSize()),

  CONSUMER_REQUEST_ERROR_COUNT("serviceruntime.googleapis.com/api/consumer/error_count",
      MetricKind.DELTA, ValueType.INT64, add1ToInt64MetricIfError()),

  PRODUCER_REQUEST_ERROR_COUNT("serviceruntime.googleapis.com/api/producer/error_count",
      MetricKind.DELTA, ValueType.INT64, add1ToInt64MetricIfError()),

  PRODUCER_BY_CONSUMER_ERROR_COUNT(
      "serviceruntime.googleapis.com/api/producer/by_consumer/error_count", MetricKind.DELTA,
      ValueType.INT64, add1ToInt64MetricIfError()),

  CONSUMER_TOTAL_LATENCIES("serviceruntime.googleapis.com/api/consumer/total_latencies",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForRequestTimeMillis()),

  PRODUCER_TOTAL_LATENCIES("serviceruntime.googleapis.com/api/producer/total_latencies",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForRequestTimeMillis()),

  PRODUCER_BY_CONSUMER_TOTAL_LATENCIES(
      "serviceruntime.googleapis.com/api/producer/by_consumer/total_latencies", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForRequestTimeMillis()),

  CONSUMER_BACKEND_LATENCIES("serviceruntime.googleapis.com/api/consumer/backend_latencies",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForBackendTimeMillis()),

  PRODUCER_BACKEND_LATENCIES("serviceruntime.googleapis.com/api/producer/backend_latencies",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForBackendTimeMillis()),

  PRODUCER_BY_CONSUMER_BACKEND_LATENCIES(
      "serviceruntime.googleapis.com/api/producer/by_consumer/backend_latencies", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForBackendTimeMillis()),

  CONSUMER_REQUEST_OVERHEAD_LATENCIES(
      "serviceruntime.googleapis.com/api/consumer/request_overhead_latencies", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForOverheadTimeMillis()),

  PRODUCER_REQUEST_OVERHEAD_LATENCIES(
      "serviceruntime.googleapis.com/api/producer/request_overhead_latencies", MetricKind.DELTA,
      ValueType.DISTRIBUTION, addDistributionMetricForOverheadTimeMillis()),

  PRODUCER_BY_CONSUMER_REQUEST_OVERHEAD_LATENCIES(
      "serviceruntime.googleapis.com/api/producer/by_consumer/request_overhead_latencies",
      MetricKind.DELTA, ValueType.DISTRIBUTION, addDistributionMetricForOverheadTimeMillis());

  private static final int TIME_SCALE = 1;
  private static final double SIZE_SCALE = 1e6;
  private static final double DISTRIBUTION_GROWTH_FACTOR = 10.0;
  private static final int DISTRIBUTION_BUCKETS = 8;

  private String name;
  private MetricKind kind;
  private ValueType type;
  private Update updater;

  private KnownMetrics(String name, MetricKind kind, ValueType type, Update updater) {
    this.name = name;
    this.type = type;
    this.kind = kind;
    this.updater = updater;
  }

  /**
   * @return the name of metrics that match this instance
   */
  public String getName() {
    return name;
  }

  /**
   * @return the {@code MetricKind} of metrics that match this instance
   */
  public MetricKind getKind() {
    return kind;
  }

  /**
   * @return the {@code ValueType} of metrics that match this instance
   */
  public ValueType getType() {
    return type;
  }

  public Update getUpdater() {
    return updater;
  }

  /**
   * Determines if {@code d} matches this {@code KnownMetric} instance.
   *
   * @param d a {@code LabelDescriptor}
   */
  public boolean matches(MetricDescriptor d) {
    return d.getName() == name && d.getMetricKind() == kind && d.getValueType() == type;
  }

  /**
   * Determines if the given {@code MetricDescriptor} is supported.
   *
   * @param d a {@code MetricDescriptor}
   * @return {@code true} if the {@code MetricDescriptor} is supported, otherwise {@code false}
   */
  public static boolean isSupported(MetricDescriptor d) {
    for (KnownMetrics m : values()) {
      if (m.matches(d)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds metrics to an operation from the provided request data
   *
   * @param info contains request data to be reported
   * @param o the {@code Operation.Builder} to which metrics will be added
   */
  public void performUpdate(ReportRequestInfo info, Operation.Builder o) {
    if (updater != null) {
      updater.update(name, info, o);
    }
  }

  /**
   * Update defines a function that allows a {@code ReportRequestInfo} to be used to update the the
   * metrics of an operation to be added to a {@code ReportRequest}.
   */
  private static interface Update {
    /**
     * Updates the metrics in {@code op} with the given {@code name} from {@code info}.
     *
     * @param name the name of a label to update
     * @param info the {@code ReportRequestInfo} from which to update the labels
     * @param op an {@code Operation} in a {@code ReportRequest}.
     */
    void update(String name, ReportRequestInfo info, Operation.Builder op);
  }

  static Distribution newSizeDistribution() {
    return Distributions.createExponential(DISTRIBUTION_BUCKETS, DISTRIBUTION_GROWTH_FACTOR,
        SIZE_SCALE);
  }

  static Distribution newTimeDistribution() {
    return Distributions.createExponential(DISTRIBUTION_BUCKETS, DISTRIBUTION_GROWTH_FACTOR,
        TIME_SCALE);
  }

  private static Update add1ToInt64Metric() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        add1ToInt64MetricValue(name, op);
      }
    };
  }

  private static Update add1ToInt64MetricIfError() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getResponseCode() >= 400) {
          add1ToInt64MetricValue(name, op);
        }
      }
    };
  }

  private static void addInt64MetricValue(String name, long value, Operation.Builder op) {
    op.addMetricValueSets(MetricValueSet.newBuilder().setMetricName(name).addMetricValues(
        MetricValue.newBuilder().setDoubleValue(value).build()));
  }

  private static void add1ToInt64MetricValue(String name, Operation.Builder op) {
    addInt64MetricValue(name, 1L, op);
  }

  private static Update addDistributionMetricForResponseSize() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getResponseSize() > 0) {
          addSizeDistributionMetricValue(name, info.getResponseSize(), op);
        }
      }
    };
  }

  private static Update addDistributionMetricForRequestSize() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getRequestSize() > 0) {
          addSizeDistributionMetricValue(name, info.getRequestSize(), op);
        }
      }
    };
  }

  private static Update addDistributionMetricForRequestTimeMillis() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getRequestTimeMillis() > 0) {
          addTimeDistributionMetricValue(name, info.getRequestTimeMillis(), op);
        }
      }
    };
  }

  private static Update addDistributionMetricForBackendTimeMillis() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getBackendTimeMillis() > 0) {
          addTimeDistributionMetricValue(name, info.getBackendTimeMillis(), op);
        }
      }
    };
  }

  private static Update addDistributionMetricForOverheadTimeMillis() {
    return new Update() {
      @Override
      public void update(String name, ReportRequestInfo info, Operation.Builder op) {
        if (info.getOverheadTimeMillis() > 0) {
          addTimeDistributionMetricValue(name, info.getOverheadTimeMillis(), op);
        }
      }
    };
  }

  private static void addTimeDistributionMetricValue(String name, long value,
      Operation.Builder op) {
    Distribution d = Distributions.addSample(value, newTimeDistribution());
    op.addMetricValueSets(MetricValueSet.newBuilder().setMetricName(name).addMetricValues(
        MetricValue.newBuilder().setDistributionValue(d).build()));
  }

  private static void addSizeDistributionMetricValue(String name, long value,
      Operation.Builder op) {
    Distribution d = Distributions.addSample(value, newSizeDistribution());
    op.addMetricValueSets(MetricValueSet.newBuilder().setMetricName(name).addMetricValues(
        MetricValue.newBuilder().setDistributionValue(d).build()));
  }
}
