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
