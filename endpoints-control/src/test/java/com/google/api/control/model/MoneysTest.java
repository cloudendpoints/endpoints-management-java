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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.type.Money;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the behavior in Moneys.
 */
@RunWith(JUnit4.class)
public class MoneysTest {
  private static Money BAD_CURRENCY = Money.newBuilder().setCurrencyCode("this is bad").build();
  private static Money[] MISMATCHED_UNITS =
      {Money.newBuilder().setCurrencyCode("JPY").setUnits(-1).setNanos(1).build(),
          Money.newBuilder().setCurrencyCode("JPY").setUnits(1).setNanos(-1).build()};
  private static Money NANO_OOB =
      Money.newBuilder().setCurrencyCode("EUR").setUnits(0).setNanos(Moneys.MAX_NANOS + 1).build();
  private static Money[] OK =
      {Money.newBuilder().setCurrencyCode("JPY").setUnits(1).setNanos(1).build(),
          Money.newBuilder().setCurrencyCode("JPY").setUnits(-1).setNanos(-1).build(),
          Money.newBuilder().setCurrencyCode("JPY").setUnits(0).setNanos(Moneys.MAX_NANOS).build()};
  private static Money SOME_YEN =
      Money.newBuilder().setCurrencyCode("JPY").setUnits(3).setNanos(0).build();
  private static Money SOME_YEN_DEBT =
      Money.newBuilder().setCurrencyCode("JPY").setUnits(-2).setNanos(-1).build();
  private static Money SOME_USD =
      Money.newBuilder().setCurrencyCode("USD").setUnits(1).setNanos(0).build();
  private static Money LARGE_YEN =
      Money.newBuilder().setCurrencyCode("JPY").setUnits(Long.MAX_VALUE - 1).setNanos(0).build();
  private static Money LARGE_YEN_DEBT =
      Money.newBuilder().setCurrencyCode("JPY").setUnits(Long.MIN_VALUE + 1).setNanos(0).build();


  @Test
  public void checkValidShouldFailWhenNoCurrencyIsSet() {
    try {
      Moneys.checkValid(Money.newBuilder().build());
      fail("should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void checkValidShouldFailWhenTheCurrencyIsBad() {
    try {
      Moneys.checkValid(BAD_CURRENCY);
      fail("should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void checkValidShouldFailWhenUnitsAndNanosAreMismatched() {
    for (Money money : MISMATCHED_UNITS) {
      try {
        Moneys.checkValid(money);
        fail("should have raised IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void checkValidShouldFailWhenNanosAreOob() {
    try {
      Moneys.checkValid(NANO_OOB);
      fail("should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void checkValidShouldSucceedForOkValues() {
    for (Money money : OK) {
      Moneys.checkValid(money);
    }
  }

  @Test
  public void addShouldFailOnCurrencyMismatch() {
    try {
      Moneys.add(SOME_USD, SOME_YEN);
      fail("should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void addShouldFailOnUnallowedPositiveOverflow() {
    try {
      Moneys.add(SOME_YEN, LARGE_YEN);
      fail("should have raised ArithmeticException");
    } catch (ArithmeticException e) {
      // expected
    }
  }

  @Test
  public void addShouldFailOnUnallowedNegativeOverflow() {
    try {
      Moneys.add(SOME_YEN_DEBT, LARGE_YEN_DEBT);
      fail("should have raised ArithmeticException");
    } catch (ArithmeticException e) {
      // expected
    }
  }

  @Test
  public void addShouldAllowPositiveOverflows() {
    Money sum = Moneys.add(SOME_YEN, LARGE_YEN, true);
    assertEquals(sum.getNanos(), Moneys.MAX_NANOS);
    assertEquals(sum.getUnits(), Long.MAX_VALUE);
  }

  @Test
  public void addShouldAllowNegativeOverflows() {
    Money sum = Moneys.add(SOME_YEN_DEBT, LARGE_YEN_DEBT, true);
    assertEquals(sum.getNanos(), -Moneys.MAX_NANOS);
    assertEquals(sum.getUnits(), Long.MIN_VALUE);
  }

  @Test
  public void addShouldBeOKWhenNanosHaveTheSameSign() {
    Money sum = Moneys.add(SOME_YEN, SOME_YEN);
    assertEquals(sum.getNanos(), 2 * SOME_YEN.getNanos());
    assertEquals(sum.getUnits(), 2 * SOME_YEN.getUnits());
  }

  @Test
  public void addShouldBeOKWhenNanosHaveDifferentSigns() {
    Money sum = Moneys.add(SOME_YEN, SOME_YEN_DEBT);
    assertEquals(sum.getNanos(), Moneys.MAX_NANOS);
    assertEquals(sum.getUnits(), SOME_YEN_DEBT.getUnits() + SOME_YEN.getUnits() - 1);
  }
}
