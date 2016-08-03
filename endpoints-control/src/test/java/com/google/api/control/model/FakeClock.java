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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.google.api.client.util.Clock;

public class FakeClock implements Clock {
  private final AtomicLong millis = new AtomicLong();

  /** Advances the ticker value by {@code time} in {@code timeUnit}. */
  public FakeClock tick(long time, TimeUnit timeUnit) {
    millis.addAndGet(timeUnit.toMillis(time));
    return this;
  }

  @Override
  public long currentTimeMillis() {
    return millis.get();
  }
}
