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

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.scc.model.Distributions;
import com.google.api.scc.model.Moneys;
import com.google.api.scc.model.Timestamps;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValue.Builder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

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
    Signing.putLabels(h, value.getLabels());
    if (value.getValueCase() == MetricValue.ValueCase.MONEY_VALUE) {
      h.putChar('\0');
      h.putString(value.getMoneyValue().getCurrencyCode(), StandardCharsets.UTF_8);
    }
    return h;
  }

  /**
   * Obtains the {@hashCode} for the contents of {@code value}.
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
   * If {@kind} is {@code MetricKind.DELTA} then the result contains a combination of values in
   * prior and latest. For all other kinds, it's sufficient to return the metric with the latest end
   * time.
   *
   * @param prior a {@code MetricValue} instance
   * @param latest a {@code MetricValue}, expected to be a later version of {@code prior}
   *
   * @return a new {@code MetricValue} that combines prior and latest depending on {@kind}
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
      case MONEY_VALUE:
        builder.setMoneyValue(Moneys.add(prior.getMoneyValue(), latest.getMoneyValue()));
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
