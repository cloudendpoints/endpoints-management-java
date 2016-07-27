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

package com.google.api.scc.model;

import java.util.Comparator;

import com.google.common.base.Ticker;
import com.google.protobuf.Timestamp;

public final class Timestamps {
  private static final int NANOS_PER_SECOND = 1000000000;

  private Timestamps() {}

  /**
   * A {@code Comparator} of {@link Timestamp}
   */
  public static Comparator<Timestamp> COMPARATOR = new Comparator<Timestamp>() {
    @Override
    public int compare(Timestamp o1, Timestamp o2) {
      int secondsOrder = Long.compare(o1.getSeconds(), o2.getSeconds());
      if (secondsOrder != 0) {
        return secondsOrder;
      } else {
        return Long.compare(o1.getNanos(), o2.getNanos());
      }
    }
  };

  /**
   * Obtain the current time from a {@link Ticker}
   *
   * @param ticker gives the current time
   * @return a {@code Timestamp} corresponding to the ticker's current value
   */
  public static Timestamp now(Ticker ticker) {
    long t = ticker.read();
    return Timestamp
        .newBuilder()
        .setNanos((int) t % NANOS_PER_SECOND)
        .setSeconds(t / NANOS_PER_SECOND)
        .build();
  }
}
