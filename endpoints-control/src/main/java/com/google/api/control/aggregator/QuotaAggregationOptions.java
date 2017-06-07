/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

/**
 * Holds values used to configure quota aggregation.
 */
public class QuotaAggregationOptions {
  public static final int DEFAULT_NUM_ENTRIES = 1000;
  public static final int DEFAULT_REFRESH_MILLIS = 1000;
  public static final int DEFAULT_TIMEOUT_MILLIS = 60000;

  private final int numEntries;
  private final int refreshMillis;
  private final int timeoutMillis;

  public QuotaAggregationOptions() {
    this(DEFAULT_NUM_ENTRIES, DEFAULT_REFRESH_MILLIS, DEFAULT_TIMEOUT_MILLIS);
  }

  public QuotaAggregationOptions(int numEntries, int refreshMillis, int timeoutMillis) {
    this.numEntries = numEntries;
    this.refreshMillis = refreshMillis;
    this.timeoutMillis = timeoutMillis;
  }

  public int getNumEntries() {
    return numEntries;
  }

  public int getRefreshMillis() {
    return refreshMillis;
  }

  public int getTimeoutMillis() {
    return timeoutMillis;
  }
}
