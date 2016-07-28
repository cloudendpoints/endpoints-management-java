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

package com.google.api.auth;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jose4j.jwk.JsonWebKeySet;
import org.junit.Test;

import java.time.Duration;

/**
 * Tests for {@link CachingJwksSupplier}.
 *
 * @author yangguan@google.com
 *
 */
public final class CachingJwksSupplierTest {
  private static final String ISSUER = "issuer";

  private final JwksSupplier jwksSupplier = mock(JwksSupplier.class);
  private final TestingTicker ticker = new TestingTicker();

  private final CachingJwksSupplier cachingJwksSupplier =
      new CachingJwksSupplier(jwksSupplier, ticker);

  private final JsonWebKeySet jwks1 = new JsonWebKeySet();
  private final JsonWebKeySet jwks2 = new JsonWebKeySet();

  @Test
  public void testSupplyCachedJwks() {
    when(jwksSupplier.supply(ISSUER)).thenReturn(jwks1);

    assertEquals(jwks1, cachingJwksSupplier.supply(ISSUER));
    // Verify that the delegating jwksSupplier is called to load the cache.
    verify(jwksSupplier, only()).supply(ISSUER);

    reset(jwksSupplier);
    when(jwksSupplier.supply(ISSUER)).thenReturn(jwks2);

    // Advance the ticker by 1 minute and make sure that the JWKS is correctly cached.
    ticker.advance(Duration.ofMinutes(1));
    assertEquals(jwks1, cachingJwksSupplier.supply(ISSUER));
    verify(jwksSupplier, never()).supply(ISSUER);

    // Advance the ticker by 5 minute to cause the cached JWKS to expire.
    ticker.advance(Duration.ofMinutes(5));
    assertEquals(jwks2, cachingJwksSupplier.supply(ISSUER));
    verify(jwksSupplier, only()).supply(ISSUER);
  }
}
