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

package com.google.api.config;

import com.google.api.Service;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.concurrent.TimeUnit;

/**
 * Fetches a service configuration from Google Service Management APIs.
 *
 * The fetched service configuration is memoized for 10 minutes.
 */
public class ServiceConfigFetcher {

  private final Supplier<Service> serviceSupplier;

  @VisibleForTesting
  ServiceConfigFetcher(Supplier<Service> supplier) {
    this.serviceSupplier = Suppliers.memoizeWithExpiration(supplier, 10, TimeUnit.MINUTES);
  }

  public Service fetch() {
    return this.serviceSupplier.get();
  }

  public static ServiceConfigFetcher create() {
    return new ServiceConfigFetcher(ServiceConfigSupplier.create());
  }
}
