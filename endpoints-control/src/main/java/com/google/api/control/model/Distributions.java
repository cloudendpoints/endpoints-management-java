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

package com.google.api.control.model;

import com.google.api.servicecontrol.v1.Distribution;
import com.google.api.servicecontrol.v1.Distribution.BucketOptionCase;
import com.google.api.servicecontrol.v1.Distribution.Builder;
import com.google.api.servicecontrol.v1.Distribution.ExplicitBuckets;
import com.google.api.servicecontrol.v1.Distribution.ExponentialBuckets;
import com.google.api.servicecontrol.v1.Distribution.LinearBuckets;
import com.google.common.collect.Sets;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for working with {@link Distribution} instances.
 */
public final class Distributions {
  private static final String MSG_BUCKET_COUNTS_MISMATCH = "Bucket counts do not match";
  private static final String MSG_BUCKET_OPTIONS_MISMATCH = "Bucket options do not match";
  private static final String MSG_UNKNOWN_BUCKET_OPTION_TYPE = "Unknown bucket option type";
  private static final String MSG_SOME_BOUNDS_ARE_THE_SAME =
      "Illegal bounds, at least two bounds are the same!";
  private static final String MSG_BAD_DIST_LOW_BUCKET_COUNT =
      "cannot update a distribution with a low bucket count";
  private static final String MSG_DOUBLE_TOO_LOW = "%s should be > %f";
  private static final String MSG_BAD_NUM_FINITE_BUCKETS = "number of finite buckets should be > 0";
  private static final double TOLERANCE = 1e-5;
  private static final Logger log = Logger.getLogger(Distributions.class.getName());

  private Distributions() {}

  /**
   * Creates an {@code Distribution} with {@code ExponentialBuckets}.
   *
   * @param numFiniteBuckets initializes the number of finite buckets
   * @param growthFactor initializes the growth factor
   * @param scale initializes the scale
   * @return a {@code Distribution} with {@code ExponentialBuckets}
   * @throws IllegalArgumentException if a bad input prevents creation.
   */
  public static Distribution createExponential(int numFiniteBuckets, double growthFactor,
      double scale) {
    if (numFiniteBuckets <= 0) {
      throw new IllegalArgumentException(MSG_BAD_NUM_FINITE_BUCKETS);
    }
    if (growthFactor <= 1.0) {
      throw new IllegalArgumentException(String.format(MSG_DOUBLE_TOO_LOW, "growth factor", 1.0));
    }
    if (scale <= 0.0) {
      throw new IllegalArgumentException(String.format(MSG_DOUBLE_TOO_LOW, "scale", 0.0));
    }
    ExponentialBuckets buckets = ExponentialBuckets.newBuilder().setGrowthFactor(growthFactor)
        .setNumFiniteBuckets(numFiniteBuckets).setScale(scale).build();
    Builder builder = Distribution.newBuilder().setExponentialBuckets(buckets);
    for (int i = 0; i < numFiniteBuckets + 2; i++) {
      builder.addBucketCounts(0L);
    }
    return builder.build();
  }

  /**
   * Creates a {@code Distribution} with {@code LinearBuckets}.
   *
   * @param numFiniteBuckets initializes the number of finite buckets
   * @param width initializes the width of each bucket
   * @param offset initializes the offset of the start bucket
   * @return a {@code Distribution} with {@code LinearBuckets}
   * @throws IllegalArgumentException if a bad input prevents creation.
   */
  public static Distribution createLinear(int numFiniteBuckets, double width, double offset) {
    if (numFiniteBuckets <= 0) {
      throw new IllegalArgumentException(MSG_BAD_NUM_FINITE_BUCKETS);
    }
    if (width <= 0.0) {
      throw new IllegalArgumentException(String.format(MSG_DOUBLE_TOO_LOW, "width", 0.0));
    }
    LinearBuckets buckets = LinearBuckets.newBuilder().setOffset(offset).setWidth(width)
        .setNumFiniteBuckets(numFiniteBuckets).build();
    Builder builder = Distribution.newBuilder().setLinearBuckets(buckets);
    for (int i = 0; i < numFiniteBuckets + 2; i++) {
      builder.addBucketCounts(0L);
    }
    return builder.build();
  }

  /**
   * Creates a {@code Distribution} with {@code ExplicitBuckets}.
   *
   * @param bounds initializes the bounds used to define the explicit buckets
   *
   * @return a {@code Distribution} with {@code ExplicitBuckets}
   * @throws IllegalArgumentException if a bad input prevents creation.
   */
  public static Distribution createExplicit(double[] bounds) {
    List<Double> allBounds = Doubles.asList(bounds);
    Set<Double> uniqueBounds = Sets.newHashSet(allBounds);
    if (allBounds.size() != uniqueBounds.size()) {
      throw new IllegalArgumentException(MSG_SOME_BOUNDS_ARE_THE_SAME);
    }
    Collections.sort(allBounds);
    ExplicitBuckets buckets = ExplicitBuckets.newBuilder().addAllBounds(allBounds).build();
    Builder builder = Distribution.newBuilder().setExplicitBuckets(buckets);
    for (int i = 0; i < allBounds.size() + 1; i++) {
      builder.addBucketCounts(0L);
    }
    return builder.build();
  }

  /**
   * Updates as new distribution that contains value added to an existing one.
   *
   * @param value the sample value
   * @param distribution a {@code Distribution}
   * @return the updated distribution
   */
  public static Distribution addSample(double value, Distribution distribution) {
    Builder builder = distribution.toBuilder();
    switch (distribution.getBucketOptionCase()) {
      case EXPLICIT_BUCKETS:
        updateStatistics(value, builder);
        updateExplicitBuckets(value, builder);
        return builder.build();
      case EXPONENTIAL_BUCKETS:
        updateStatistics(value, builder);
        updateExponentialBuckets(value, builder);
        return builder.build();
      case LINEAR_BUCKETS:
        updateStatistics(value, builder);
        updateLinearBuckets(value, builder);
        return builder.build();
      default:
        throw new IllegalArgumentException(MSG_UNKNOWN_BUCKET_OPTION_TYPE);
    }
  }

  /**
   * Merge {@code prior} with {@code latest}.
   *
   * @param prior a {@code Distribution} instance
   * @param latest a {@code Distribution}, expected to be a later version of {@code prior}
   *
   * @return a new {@code Distribution} that combines the statistics and buckets of the earlier two
   * @throws IllegalArgumentException if the bucket options of {@code prior} and {@code latest}
   *         don't match
   * @throws IllegalArgumentException if the bucket counts of {@code prior} and {@code latest} dont'
   *         match
   */
  public static Distribution merge(Distribution prior, Distribution latest) {
    if (!bucketsNearlyEquals(prior, latest)) {
      throw new IllegalArgumentException(MSG_BUCKET_OPTIONS_MISMATCH);
    }
    if (prior.getBucketCountsCount() != latest.getBucketCountsCount()) {
      throw new IllegalArgumentException(MSG_BUCKET_COUNTS_MISMATCH);
    }
    if (prior.getCount() == 0) {
      return latest;
    }

    // Merge the distribution statistics
    Builder builder = latest.toBuilder();
    long oldCount = latest.getCount();
    double oldMean = latest.getMean();
    builder.setCount(prior.getCount() + oldCount);
    builder.setMaximum(Math.max(prior.getMaximum(), latest.getMaximum()));
    builder.setMinimum(Math.min(prior.getMinimum(), latest.getMinimum()));
    double newMean = (oldCount * oldMean + prior.getCount() * prior.getMean()) / builder.getCount();
    builder.setMean(newMean);
    double oldSumOfSquaredDeviation = latest.getSumOfSquaredDeviation();
    double newSumOfSquaredDeviation = oldSumOfSquaredDeviation + prior.getSumOfSquaredDeviation()
        + (oldCount * Math.pow((builder.getMean() - oldMean), 2))
        + (prior.getCount() * Math.pow((builder.getMean() - prior.getMean()), 2));
    builder.setSumOfSquaredDeviation(newSumOfSquaredDeviation);

    // Merge the bucket counts
    for (int i = 0; i < latest.getBucketCountsCount(); i++) {
      builder.setBucketCounts(i, prior.getBucketCounts(i) + latest.getBucketCounts(i));
    }
    return builder.build();
  }

  private static boolean bucketsNearlyEquals(Distribution a, Distribution b) {
    BucketOptionCase caseA = a.getBucketOptionCase();
    BucketOptionCase caseB = b.getBucketOptionCase();
    if (caseA != caseB) {
      return false;
    }
    switch (caseA) {
      case EXPLICIT_BUCKETS:
        return bucketsNearlyEquals(a.getExplicitBuckets(), b.getExplicitBuckets());
      case EXPONENTIAL_BUCKETS:
        return bucketsNearlyEquals(a.getExponentialBuckets(), b.getExponentialBuckets());
      case LINEAR_BUCKETS:
        return bucketsNearlyEquals(a.getLinearBuckets(), b.getLinearBuckets());
      default:
        return false;
    }
  }

  private static boolean bucketsNearlyEquals(ExplicitBuckets a, ExplicitBuckets b) {
    if (a.getBoundsCount() != b.getBoundsCount()) {
      return false;
    }
    for (int i = 0; i < b.getBoundsCount(); i++) {
      if (!DoubleMath.fuzzyEquals(a.getBounds(i), b.getBounds(i), TOLERANCE)) {
        return false;
      }
    }
    return true;
  }

  private static boolean bucketsNearlyEquals(ExponentialBuckets a, ExponentialBuckets b) {
    return ((a.getNumFiniteBuckets() == b.getNumFiniteBuckets())
        && (DoubleMath.fuzzyEquals(a.getGrowthFactor(), b.getGrowthFactor(), TOLERANCE)
            && (DoubleMath.fuzzyEquals(a.getScale(), b.getScale(), TOLERANCE))));
  }

  private static boolean bucketsNearlyEquals(LinearBuckets a, LinearBuckets b) {
    return ((a.getNumFiniteBuckets() == b.getNumFiniteBuckets())
        && (DoubleMath.fuzzyEquals(a.getWidth(), b.getWidth(), TOLERANCE)
            && (DoubleMath.fuzzyEquals(a.getOffset(), b.getOffset(), TOLERANCE))));
  }

  private static void updateStatistics(double value, Builder distribution) {
    long oldCount = distribution.getCount();
    if (oldCount == 0) {
      distribution.setCount(1);
      distribution.setMaximum(value);
      distribution.setMinimum(value);
      distribution.setMean(value);
      distribution.setSumOfSquaredDeviation(0);
    } else {
      double oldMean = distribution.getMean();
      double newMean = ((oldCount * oldMean) + value) / (oldCount + 1);
      double deltaSumOfSquares = (value - oldMean) * (value - newMean);
      distribution.setCount(oldCount + 1);
      distribution.setMean(newMean);
      distribution.setMaximum(Math.max(value, distribution.getMaximum()));
      distribution.setMinimum(Math.min(value, distribution.getMinimum()));
      distribution
          .setSumOfSquaredDeviation(deltaSumOfSquares + distribution.getSumOfSquaredDeviation());
    }
  }

  private static void updateLinearBuckets(double value, Builder distribution) {
    LinearBuckets buckets = distribution.getLinearBuckets();
    if (distribution.getBucketCountsCount() != buckets.getNumFiniteBuckets() + 2) {
      throw new IllegalArgumentException(MSG_BAD_DIST_LOW_BUCKET_COUNT);
    }

    // Determine the offset the value fits into
    double upper = buckets.getWidth() * buckets.getNumFiniteBuckets() + buckets.getOffset();
    int index = 0;
    if (value >= upper) {
      index = buckets.getNumFiniteBuckets() + 1;
    } else if (value > buckets.getOffset()) {
      index = 1 + ((int) Math.round((value - buckets.getOffset()) / buckets.getWidth()));
    }
    long newCount = distribution.getBucketCounts(index) + 1;
    log.log(Level.FINE, "Updating explicit bucket {0} to {1} for {2}",
        new Object[] {index, newCount, value});
    distribution.setBucketCounts(index, newCount);
  }

  private static void updateExponentialBuckets(double value, Builder distribution) {
    ExponentialBuckets buckets = distribution.getExponentialBuckets();
    if (distribution.getBucketCountsCount() != buckets.getNumFiniteBuckets() + 2) {
      throw new IllegalArgumentException(MSG_BAD_DIST_LOW_BUCKET_COUNT);
    }

    // Determine the offset the value fits into
    int index = 0;
    if (value > buckets.getScale()) {
      index =
          1 + (int) (Math.log(value / buckets.getScale()) / Math.log(buckets.getGrowthFactor()));
      index = Math.min(buckets.getNumFiniteBuckets() + 1, index);
    }
    long newCount = distribution.getBucketCounts(index) + 1;
    if (log.isLoggable(Level.FINE)) {
      log.log(Level.FINE, "Updating explicit bucket {0} to {1} for {2}",
          new Object[] {index, newCount, value});
    }
    distribution.setBucketCounts(index, newCount);
  }

  private static void updateExplicitBuckets(double value, Builder distribution) {
    ExplicitBuckets buckets = distribution.getExplicitBuckets();
    if (distribution.getBucketCountsCount() != buckets.getBoundsCount() + 1) {
      throw new IllegalArgumentException(MSG_BAD_DIST_LOW_BUCKET_COUNT);
    }

    // Determine the offset for the value using Collections.binarySearch.
    //
    // Note that when the value is not in the list the result of
    // Collections.binarySearch is -ve: - (insertion point) - 1.
    int index = Collections.binarySearch(buckets.getBoundsList(), value);
    if (index < 0) {
      index = -index - 1;
    } else {
      index += 1;
    }
    long newCount = distribution.getBucketCounts(index) + 1;
    if (log.isLoggable(Level.FINE)) {
      log.log(Level.FINE, "Updating explicit bucket {0} to {1} for {2}",
          new Object[] {index, newCount, value});
    }
    distribution.setBucketCounts(index, newCount);
  }
}
