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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.servicecontrol.v1.Distribution;
import com.google.common.collect.Ordering;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Tests the behavior of {@link Distributions}.
 */
@RunWith(JUnit4.class)
public class DistributionsTest {
  private static final double TOLERANCE = 1e-5;
  private static double UNDERFLOW_SAMPLE = 1e-5;
  private static double OVERFLOW_SAMPLE = 1e5;
  private static double LOW_SAMPLE = 0.11;
  private static double HIGH_SAMPLE = 0.5;
  private static SamplesAndBuckets[] TEST_SAMPLES_AND_BUCKETS = new SamplesAndBuckets[] {
      new SamplesAndBuckets(new double[] {UNDERFLOW_SAMPLE}, new int[] {1, 0, 0, 0, 0}),
      new SamplesAndBuckets(new double[] {LOW_SAMPLE, LOW_SAMPLE}, new int[] {0, 2, 0, 0, 0}),
      new SamplesAndBuckets(new double[] {LOW_SAMPLE, HIGH_SAMPLE, HIGH_SAMPLE},
          new int[] {0, 1, 0, 2, 0}),
      new SamplesAndBuckets(new double[] {OVERFLOW_SAMPLE}, new int[] {0, 0, 0, 0, 1}),};
  private MergeTriple[] mergeTriples;

  @Before
  public void setUpMergeTriples() {
    mergeTriples = new MergeTriple[] {
        new MergeTriple(Distributions.createExponential(3, 2, 0.1),
            Distributions.createExponential(4, 2, 0.1)),
        new MergeTriple(Distributions.createLinear(3, 0.2, 0.1),
            Distributions.createLinear(4, 0.2, 0.1)),
        new MergeTriple(Distributions.createExplicit(new double[] {0.1, 0.3}),
            Distributions.createExplicit(new double[] {0.1, 0.3, 0.5})),};
    for (MergeTriple mt : mergeTriples) {
      mt.prior = Distributions.addSample(LOW_SAMPLE, mt.prior);
      mt.similar = Distributions.addSample(HIGH_SAMPLE, mt.similar);
    }
  }

  @Test
  public void createExplicitShouldFailIfThereAreMatchingBounds() {
    try {
      Distributions.createExplicit(new double[] {0.0, 1.0, 1.0 /* repeated */, 2.0});
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void createExplicitSucceedsWithOrderedBounds() {
    Distributions.createExplicit(new double[] {0.0, 0.1, 0.2});
  }

  public void createExplicitSucceedsWithOutOfOrderBounds() {
    Distributions.createExplicit(new double[] {0.1, 0.2, 0.0});
  }

  @Test
  public void createLinearShouldFailIfNumberOfFiniteBucketsIsBad() {
    try {
      Distributions.createLinear(0 /* not > 0 */, 10.0, 0.1);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void createLinearShouldFailIfWidthIsBad() {
    try {
      Distributions.createLinear(2, -1 /* not > 0 */, -0.1);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void createLinearShouldSucceedIfAllInputsAreOK() {
    Distributions.createLinear(1, 1.0, -1.0);
  }

  @Test
  public void createExponentialShouldFailIfNumberOfFiniteBucketsIsBad() {
    try {
      Distributions.createExponential(0 /* not > 0 */, 1.1, 0.1);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void createExponentialShouldFailIfGrowthFactorIsBad() {
    try {
      Distributions.createExponential(1, 0.9 /* not > 1.0 */, 0.1);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void createExponentialShouldFailIfScaleFactorIsBad() {
    try {
      Distributions.createExponential(1, 1.1, 0.0 /* not greater than 0.0 */);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void createExponentialShouldSucceedIfAllInputsAreOK() {
    Distributions.createExponential(1, 1.1, 1.0);
  }

  @Test
  public void addSampleShouldFailForOnUninitializedDistributions() {
    try {
      Distributions.addSample(LOW_SAMPLE, Distribution.getDefaultInstance());
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void addSampleShouldUpdateExplicitBucketsOk() {
    int index = 0;
    for (SamplesAndBuckets test : TEST_SAMPLES_AND_BUCKETS) {
      Distribution distribution = newTestExplicitDistribution();
      for (double sample : test.samples) {
        distribution = Distributions.addSample(sample, distribution);
      }
      assertArrayEquals(String.format("bucketcounts for test #%d", index), test.wantedBucketCounts,
          Ints.toArray(distribution.getBucketCountsList()));
      assertDistributionStatsMatchCalcFromSample(distribution, test.samples);
      index++;
    }
  }

  @Test
  public void addSamplesShouldUpdateExponentialBucketsOk() {
    int index = 0;
    for (SamplesAndBuckets test : TEST_SAMPLES_AND_BUCKETS) {
      Distribution distribution = newTestExponentialDistribution();
      for (double sample : test.samples) {
        distribution = Distributions.addSample(sample, distribution);
      }
      assertArrayEquals(String.format("bucketcounts for test #%d", index), test.wantedBucketCounts,
          Ints.toArray(distribution.getBucketCountsList()));
      assertDistributionStatsMatchCalcFromSample(distribution, test.samples);
      index++;
    }
  }

  @Test
  public void addSamplesShouldUpdateLinearBucketsOk() {
    int index = 0;
    for (SamplesAndBuckets test : TEST_SAMPLES_AND_BUCKETS) {
      Distribution distribution = newTestLinearDistribution();
      for (double sample : test.samples) {
        distribution = Distributions.addSample(sample, distribution);
      }
      assertArrayEquals(String.format("bucketcounts for test #%d", index), test.wantedBucketCounts,
          Ints.toArray(distribution.getBucketCountsList()));
      assertDistributionStatsMatchCalcFromSample(distribution, test.samples);
      index++;
    }
  }

  @Test
  public void mergeShouldFailOnDissimlarBucketOptions() {
    Distribution expl = newTestExplicitDistribution();
    Distribution expo = newTestExponentialDistribution();
    Distribution line = newTestLinearDistribution();
    Distribution[][] permutations = new Distribution[][] {new Distribution[] {expo, expl},
        new Distribution[] {expo, line}, new Distribution[] {expl, line}};
    for (Distribution[] dissimilarPair : permutations) {
      try {
        Distributions.merge(dissimilarPair[0], dissimilarPair[1]);
        fail("Should have thrown IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void mergeShouldFailOnDissimilarBucketCounts() {
    for (MergeTriple mt : mergeTriples) {
      try {
        Distributions.merge(mt.prior, mt.dissimilar);
        fail("Should have thrown IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void mergeShouldComputeStatsCorrectly() {
    for (MergeTriple mt : mergeTriples) {
      Distribution merged = Distributions.merge(mt.prior, mt.similar);
      assertEquals(2, merged.getCount());
      assertEquals(HIGH_SAMPLE, merged.getMaximum(), TOLERANCE);
      assertEquals(LOW_SAMPLE, merged.getMinimum(), TOLERANCE);
      double wantedMean = (HIGH_SAMPLE + LOW_SAMPLE) / 2;
      assertEquals(wantedMean, merged.getMean(), TOLERANCE);
      double wantedSumSquareDeviations =
          Math.pow((wantedMean - HIGH_SAMPLE), 2) + Math.pow((wantedMean - LOW_SAMPLE), 2);
      assertEquals(wantedSumSquareDeviations, merged.getSumOfSquaredDeviation(), TOLERANCE);
    }
  }

  @Test
  public void mergeShouldComputeBucketCountsCorrectly() {
    for (MergeTriple mt : mergeTriples) {
      Distribution merged = Distributions.merge(mt.prior, mt.similar);
      for (int i = 0; i < merged.getBucketCountsCount(); i++) {
        assertEquals(mt.prior.getBucketCounts(i) + mt.similar.getBucketCounts(i),
            merged.getBucketCounts(i));
      }
    }
  }


  private static void assertDistributionStatsMatchCalcFromSample(Distribution distribution,
      double[] samples) {
    double wantedMean = DoubleMath.mean(samples);
    double wantedSumSquareDeviations = 0.0;
    for (int i = 0; i < samples.length; i++) {
      wantedSumSquareDeviations += Math.pow((wantedMean - samples[i]), 2);
    }
    assertEquals(wantedSumSquareDeviations, distribution.getSumOfSquaredDeviation(), TOLERANCE);
    assertEquals(samples.length, distribution.getCount());
    assertEquals(wantedMean, distribution.getMean(), 1e-5);
    assertEquals(Ordering.<Double>natural().max(Doubles.asList(samples)).doubleValue(),
        distribution.getMaximum(), TOLERANCE);
    assertEquals(Ordering.<Double>natural().min(Doubles.asList(samples)).doubleValue(),
        distribution.getMinimum(), TOLERANCE);
  }

  private static final Distribution newTestExplicitDistribution() {
    return Distributions.createExplicit(new double[] {0.1, 0.3, 0.5, 0.7});
  }

  private static final Distribution newTestLinearDistribution() {
    return Distributions.createLinear(3, 0.2, 0.1);
  }

  private static final Distribution newTestExponentialDistribution() {
    return Distributions.createExponential(3, 2, 0.1);
  }

  private static final class SamplesAndBuckets {
    SamplesAndBuckets(double[] samples, int[] wantedBucketCounts) {
      this.samples = samples;
      this.wantedBucketCounts = wantedBucketCounts;
    }

    double[] samples;
    int[] wantedBucketCounts;
  }

  private static final class MergeTriple {
    MergeTriple(Distribution prior, Distribution dissimilar) {
      this.prior = prior;
      this.similar = prior.toBuilder().build();
      this.dissimilar = dissimilar;
    }

    Distribution prior;
    Distribution similar;
    Distribution dissimilar;
  }
}
