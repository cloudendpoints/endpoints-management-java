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

package com.google.api.scc.aggregator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.scc.model.Timestamps;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Container that implements operation aggregation.
 *
 * Thread compatible.
 */
public class OperationAggregator {
  /**
   * Used when the {@code kinds} is not specified, or for metric names that are not specified in
   * {@kinds}
   */
  public static final MetricKind DEFAULT_KIND = MetricKind.DELTA;
  private final Operation.Builder op;
  private final Map<String, MetricKind> kinds;
  private final Map<String, Map<String, MetricValue>> metricValues;

  /**
   * Constructor.
   *
   * @param op the initial {@code Operation}
   * @param kinds specifies the {@link MetricKind} for specific metric names
   */
  public OperationAggregator(Operation op, Map<String, MetricKind> kinds) {
    if (kinds != null) {
      this.kinds = ImmutableMap.copyOf(kinds);
    } else {
      this.kinds = ImmutableMap.of();
    }
    this.op = op.toBuilder().clearMetricValueSets();
    this.metricValues = Maps.newHashMap();
    mergeMetricValues(op);
  }

  /**
   * Combines {@code op} with the other operation(s) merged into this instance.
   *
   * @param other an {@code Operation} to merge into the aggregate.
   */
  public void add(Operation other) {
    op.addAllLogEntries(other.getLogEntriesList());
    mergeMetricValues(other);
    mergeTimestamps(other);
  }

  /**
   * @return an {@code Operation} that combines all the merged {@code Operation}s
   */
  public Operation asOperation() {
    Set<String> keySet = Sets.newTreeSet(this.metricValues.keySet());
    for (String name : keySet) {
      Collection<MetricValue> values = this.metricValues.get(name).values();
      op.addMetricValueSets(
          MetricValueSet.newBuilder().setMetricName(name).addAllMetricValues(values));
    }
    return op.build();
  }

  private void mergeMetricValues(Operation other) {
    List<MetricValueSet> mvSets = other.getMetricValueSetsList();
    for (MetricValueSet mvSet : mvSets) {
      Map<String, MetricValue> bySignature = this.metricValues.get(mvSet.getMetricName());
      if (bySignature == null) {
        bySignature = Maps.newHashMap();
        this.metricValues.put(mvSet.getMetricName(), bySignature);
      }
      for (MetricValue mv : mvSet.getMetricValuesList()) {
        String signature = MetricValues.sign(mv).toString();
        MetricValue prior = bySignature.get(signature);
        if (prior == null) {
          bySignature.put(signature, mv);
        } else {
          MetricKind kind = this.kinds.get(mvSet.getMetricName());
          if (kind == null) {
            kind = DEFAULT_KIND;
          }
          bySignature.put(signature, MetricValues.merge(kind, prior, mv));
        }
      }
    }
  }

  private void mergeTimestamps(Operation other) {
    if (Timestamps.COMPARATOR.compare(other.getStartTime(), op.getStartTime()) == -1) {
      op.setStartTime(other.getStartTime());
    }
    if (Timestamps.COMPARATOR.compare(op.getEndTime(), other.getEndTime()) == -1) {
      op.setEndTime(other.getEndTime());
    }
  }
}
