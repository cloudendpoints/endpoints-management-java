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

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.control.model.Distributions;
import com.google.api.control.model.Timestamps;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValue.Builder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide functions that enable aggregation of {@link MetricValue}s.
 */
public final class MetricValues {
  private static final String MSG_NOT_MERGABLE = "Metric type not mergeabe";
  private static final String MSG_CANNOT_MERGE_DIFFERENT_TYPES =
      "Cannot merge metrics with different types of value";
  private static final Logger log = Logger.getLogger(MetricValues.class.getName());

  private MetricValues() {}

  /**
   * Updates {@code h} with the contents of {@code value}.
   *
   * @param h a {@link Hasher}
   * @param value a {@code MetricValue} to be added to the hash
   * @return the {@code Hasher}, to allow fluent-style usage
   */
  public static Hasher putMetricValue(Hasher h, MetricValue value) {
    Signing.putLabels(h, value.getLabelsMap());
    return h;
  }

  /**
   * Obtains the {@code HashCode} for the contents of {@code value}.
   *
   * @param value a {@code MetricValue} to be signed
   * @return the {@code HashCode} corresponding to {@code value}
   */
  public static HashCode sign(MetricValue value) {
    Hasher h = Hashing.md5().newHasher();
    return putMetricValue(h, value).hash();
  }

  /**
   * Merge {@code prior} with {@code latest}.
   *
   * If {@code kind} is {@code MetricKind.DELTA} then the result contains a combination of values in
   * prior and latest. For all other kinds, it's sufficient to return the metric with the latest end
   * time.
   *
   * @param kind the {@code MetricKind}
   * @param prior a {@code MetricValue} instance
   * @param latest a {@code MetricValue}, expected to be a later version of {@code prior}
   *
   * @return a new {@code MetricValue} that combines prior and latest depending on {@code kind}
   * @throws IllegalArgumentException if the {@code prior} and {@code latest} are have different
   *         types of value, or if the type is not mergeable
   */
  public static MetricValue merge(MetricKind kind, MetricValue prior, MetricValue latest) {
    if (prior.getValueCase() != latest.getValueCase()) {
      log.log(Level.WARNING, "Could not merge different types of metric: {0}, {1}",
          new Object[] {prior, latest});
      throw new IllegalArgumentException(MSG_CANNOT_MERGE_DIFFERENT_TYPES);
    }
    if (kind == MetricKind.DELTA) {
      Builder builder = latest.toBuilder();
      mergeTimestamps(builder, prior, latest);
      mergeValues(builder, prior, latest);
      return builder.build();
    } else if (Timestamps.COMPARATOR.compare(prior.getEndTime(), latest.getEndTime()) == -1) {
      return latest;
    } else {
      return prior;
    }
  }

  private static void mergeValues(Builder builder, MetricValue prior, MetricValue latest) {
    switch (latest.getValueCase()) {
      case DOUBLE_VALUE:
        builder.setDoubleValue(prior.getDoubleValue() + latest.getDoubleValue());
        break;
      case DISTRIBUTION_VALUE:
        builder.setDistributionValue(
            Distributions.merge(prior.getDistributionValue(), latest.getDistributionValue()));
        break;
      case INT64_VALUE:
        builder.setInt64Value(prior.getInt64Value() + latest.getInt64Value());
        break;
      default:
        log.log(Level.WARNING, "Could not merge logs with unmergable metric types: {0}, {1}",
            new Object[] {prior, latest});
        throw new IllegalArgumentException(MSG_NOT_MERGABLE);
    }
  }

  private static void mergeTimestamps(Builder builder, MetricValue prior, MetricValue latest) {
    if (Timestamps.COMPARATOR.compare(prior.getEndTime(), latest.getEndTime()) > 0) {
      builder.setEndTime(prior.getEndTime());
    }
    if (Timestamps.COMPARATOR.compare(prior.getStartTime(), latest.getStartTime()) < 0) {
      builder.setStartTime(prior.getStartTime());
    }
  }
}
