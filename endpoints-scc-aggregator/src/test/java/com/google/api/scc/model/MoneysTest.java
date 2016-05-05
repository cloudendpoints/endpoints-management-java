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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.type.Money;

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
