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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.protobuf.Timestamp;

/**
 * Tests {@link Timestamps}
 */
@RunWith(JUnit4.class)
public class TimestampsTest {
  private static final Timestamp EARLIEST =
      Timestamp.newBuilder().setNanos(1).setSeconds(1).build();
  private static final Timestamp EARLY = Timestamp.newBuilder().setNanos(1).setSeconds(100).build();
  private static final Timestamp LATER = Timestamp.newBuilder().setNanos(2).setSeconds(100).build();
  private static final Timestamp LATEST =
      Timestamp.newBuilder().setNanos(100).setSeconds(100).build();
  private static final Timestamp[][] TESTS = new Timestamp[][] {new Timestamp[] {EARLIEST, EARLY},
      new Timestamp[] {EARLY, LATER}, new Timestamp[] {LATER, LATEST},};

  @Test
  public void comparatorShouldOrderTimestampsCorrectly() {
    for (Timestamp[] timestamps : TESTS) {
      assertEquals(0, Timestamps.COMPARATOR.compare(timestamps[0], timestamps[0]));
      assertEquals(-1, Timestamps.COMPARATOR.compare(timestamps[0], timestamps[1]));
      assertEquals(1, Timestamps.COMPARATOR.compare(timestamps[1], timestamps[0]));
    }
  }
}
