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

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.control.model.Timestamps;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.QuotaOperation;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by tangd on 5/22/17.
 */
public class QuotaOperationAggregator {
  private static final Logger log = Logger.getLogger(QuotaOperationAggregator.class.getName());
  private QuotaOperation.Builder op;
  private Map<String, MetricValue> metricValueSets;

  public QuotaOperationAggregator(QuotaOperation op) {
    this.op = op.toBuilder().clearQuotaMetrics();
    this.metricValueSets = Maps.newHashMap();
    mergeOperation(op);
  }

  public void mergeOperation(QuotaOperation op) {
    for (MetricValueSet mvSet : op.getQuotaMetricsList()) {
      MetricValue val = metricValueSets.get(mvSet.getMetricName());
      if (val == null) {
        metricValueSets.put(mvSet.getMetricName(), mvSet.getMetricValues(0));
      } else {
        metricValueSets.put(
            mvSet.getMetricName(),
            MetricValues.merge(MetricKind.DELTA, val, mvSet.getMetricValues(0)));
      }
    }
  }

  public QuotaOperation asQuotaOperation() {
    QuotaOperation.Builder op = this.op.clone().clearQuotaMetrics();
    for (Map.Entry<String, MetricValue> entry : metricValueSets.entrySet()) {
      op.addQuotaMetrics(MetricValueSet.newBuilder()
          .setMetricName(entry.getKey())
          .addMetricValues(entry.getValue()));
    }
    return op.build();
  }

  private MetricValue mergeDeltaMetricValue(MetricValue from, MetricValue to) {
    if (to.getValueCase() != from.getValueCase()) {
      log.log(Level.WARNING, "Could not merge different types of metric: {0}, {1}",
          new Object[] {from, to});
      return to;
    }

    MetricValue.Builder builder = to.toBuilder();
    if (from.hasStartTime()) {
      if (!to.hasStartTime() ||
          Timestamps.COMPARATOR.compare(from.getStartTime(), to.getStartTime()) == -1) {
        builder.setStartTime(from.getStartTime());
      }
    }

    if (from.hasEndTime()) {
      if (!to.hasEndTime() ||
          Timestamps.COMPARATOR.compare(to.getEndTime(), from.getEndTime()) == -1) {
        builder.setEndTime(from.getEndTime());
      }
    }

    MetricValues.mergeValues(builder, to, from);
    switch (to.getValueCase()) {
      case INT64_VALUE:
        builder.setInt64Value(to.getInt64Value() + from.getInt64Value());
        break;
      default:
        log.log(Level.WARNING, "Unknown metric kind for: {0}", new Object[]{from});
        break;
    }
    return builder.build();
  }
}
