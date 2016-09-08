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

import com.google.api.client.util.Clock;
import com.google.protobuf.Timestamp;

import java.util.Comparator;

public final class Timestamps {
  private static final int MILLIS_PER_SECOND = 1000;
  private static final int NANOS_PER_MILLI = 1000000;

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
   * Obtain the current time from a {@link Clock}
   *
   * @param clock gives the current time
   * @return a {@code Timestamp} corresponding to the ticker's current value
   */
  public static Timestamp now(Clock clock) {
    return fromEpoch(clock.currentTimeMillis());
  }

  /**
   * Obtain the current time from the unix epoch
   *
   * @param epochMillis gives the current time in milliseconds since since the epoch
   * @return a {@code Timestamp} corresponding to the ticker's current value
   */
  public static Timestamp fromEpoch(long epochMillis) {
    return Timestamp
        .newBuilder()
        .setNanos((int) ((epochMillis % MILLIS_PER_SECOND) * NANOS_PER_MILLI))
        .setSeconds(epochMillis / MILLIS_PER_SECOND)
        .build();
  }
}
