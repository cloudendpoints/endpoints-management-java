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

import com.google.type.Money;

/**
 * Utility methods for working with {@link Money} instances.
 */
public final class Moneys {
  private Moneys () {}

  private static int BILLION = 1000000000;
  public static int MAX_NANOS = BILLION - 1;
  private static final String MSG_POSITIVE_OVERFLOW =
      "Addition failed due to positive overflow";
  private static final String MSG_NEGATIVE_OVERFLOW =
      "Addition failed due to negative overflow";
  private static final String MSG_3_LETTERS_LONG =
      "The currency code is not 3 letters long";
  private static final String MSG_UNITS_NANOS_MISMATCH =
      "The signs of the units and nanos do not match";
  private static final String MSG_NANOS_OOB =
      "The nanos field must be between -999,999,999 and 999,999,999";
  private static final String MSG_SAME_CURRENCY =
      "Money values need the same currency to be summed";

  /**
   * Determine if an instance of {@code Money} is valid.
   *
   * @param value an instance of {@code Money}
   * @throws IllegalArgumentException if money is invalid
   */
  public static void checkValid(Money value) {
    String currencyCode = value.getCurrencyCode();
    if (currencyCode == null || currencyCode.length() != 3) {
      throw new IllegalArgumentException(MSG_3_LETTERS_LONG);
    }
    long units = value.getUnits();
    int nanos = value.getNanos();
    if ((units > 0 && nanos < 0) || (units < 0 && nanos > 0)) {
      throw new IllegalArgumentException(MSG_UNITS_NANOS_MISMATCH);
    }
    if (Math.abs(nanos) > MAX_NANOS) {
      throw new IllegalArgumentException(MSG_NANOS_OOB);
    }
  }

  /**
   * Add two instances of {@code Money}.
   *
   * Allows the value to overflow.
   *
   * @param a an instance of {@code Money}
   * @param b an instance of {@code Money}
   * @param allowOverflow indicates that overflow is allowed
   *
   * @throws IllegalArgumentException if the two instances cannot be summed
   * @throws ArithmeticError if overflow occurs when it's not allowed
   */
  public static Money add(Money a, Money b, boolean allowOverflow) {
    if (!a.getCurrencyCode().equals(b.getCurrencyCode())) {
      throw new IllegalArgumentException(MSG_SAME_CURRENCY);
    }
    SumResult nanoSum = innerSum(a.getNanos(), b.getNanos());
    long unitSumNoCarry = a.getUnits() + b.getUnits();
    long unitSum = unitSumNoCarry + nanoSum.carry;
    if (unitSum > 0 && nanoSum.sum < 0) {
      unitSum -= 1;
      nanoSum.sum += BILLION;
    } else if (unitSum < 0 && nanoSum.sum > 0) {
      unitSum -= 1;
      nanoSum.sum -= BILLION;
    }

    // Return the result, detecting overflow
    int signOfA = signOf(a);
    int signOfB = signOf(b);
    if (signOfA > 0 && signOfB > 0 && unitSum < 0) {
      if (!allowOverflow) {
       throw new ArithmeticException(MSG_POSITIVE_OVERFLOW);
      } else {
        return Money.newBuilder()
            .setCurrencyCode(a.getCurrencyCode())
            .setNanos(MAX_NANOS)
            .setUnits(Long.MAX_VALUE)
            .build();
      }
    } else if (signOfA < 0 && signOfB < 0 && (unitSumNoCarry >= 0 || unitSum >= 0)) {
      if (!allowOverflow) {
        throw new ArithmeticException(MSG_NEGATIVE_OVERFLOW);
       } else {
         return Money.newBuilder()
             .setCurrencyCode(a.getCurrencyCode())
             .setNanos(-MAX_NANOS)
             .setUnits(Long.MIN_VALUE)
             .build();
       }
    } else {
      return Money.newBuilder()
          .setCurrencyCode(a.getCurrencyCode())
          .setNanos(nanoSum.sum)
          .setUnits(unitSum)
          .build();
    }
  }

  /**
   * Add two instances of {@code Money}.
   *
   * The sum is not allowed to overflow.
   *
   * @param a an instance of {@code Money}
   * @param b an instance of {@code Money}
   *
   * @throws IllegalArgumentException if the two instances cannot be summed
   * @throws ArithmeticError if overflow occurs when it's not allowed
   */
  public static Money add(Money a, Money b) {
    return add(a, b, false);
  }

  private static SumResult innerSum(int a, int b) {
    SumResult result = new SumResult();
    result.sum = a + b;
    if (result.sum > BILLION) {
      result.carry = 1;
      result.sum -= BILLION;
    } else if (result.sum < -BILLION) {
      result.carry = -1;
      result.sum += BILLION;
    }
    return result;
  }

  private static int signOf(Money m) {
    if (m.getUnits() > 0) {
      return 1;
    } else if (m.getUnits() < 0) {
      return -1;
    } else if (m.getNanos() > 0) {
      return 1;
    } else if (m.getNanos() < 0) {
      return -1;
    } else {
      return 0;
    }
  }

  private static class SumResult {
    public int sum;
    public int carry;
  }
}
