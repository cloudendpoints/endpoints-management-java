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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.Service;
import com.google.common.base.Supplier;

import org.junit.Before;
import org.junit.Test;

/**
 * Test for {@link ServiceConfigFetcher}.
 */
public class ServiceConfigFetcherTest {
  private final Service service = Service.newBuilder().build();
  @SuppressWarnings("unchecked")
  private final Supplier<Service> supplier = (Supplier<Service>) mock(Supplier.class);
  private final ServiceConfigFetcher fetcher = new ServiceConfigFetcher(supplier);

  @Before
  public void setUp() {
  }

  @Test
  public void testFetchWithMemorization() {
    when(supplier.get()).thenReturn(service);

    assertEquals(service, this.fetcher.fetch());
    assertEquals(service, this.fetcher.fetch());

    verify(supplier, only()).get();
  }
}
