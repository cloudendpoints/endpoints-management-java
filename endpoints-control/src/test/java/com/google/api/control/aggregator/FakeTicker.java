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

package com.google.api.control.aggregator;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class FakeTicker extends Ticker {
  private final AtomicLong nanos = new AtomicLong();
  private boolean autoTick;

  public FakeTicker() {
    this(false);
  }

  public FakeTicker(boolean autoTick) {
    this.autoTick = autoTick;
  }

  /** Advances the ticker value by {@code time} in {@code timeUnit}. */
  public FakeTicker tick(long time, TimeUnit timeUnit) {
    nanos.addAndGet(timeUnit.toNanos(time));
    return this;
  }

  @Override
  public long read() {
    long res = nanos.getAndAdd(0);
    if (autoTick) {
      tick(1, TimeUnit.SECONDS);
    }
    return res;
  }
}