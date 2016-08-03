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

import org.jose4j.jwt.JwtClaims;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class CachingAuthTokenDecoderTest {
  private static final String AUTH_TOKEN = "auth-token";

  private final AuthTokenDecoder authTokenDecoder = mock(AuthTokenDecoder.class);
  private final TestingTicker testingTicker = new TestingTicker();

  private final AuthTokenDecoder cachingDecoder =
      new CachingAuthTokenDecoder(authTokenDecoder, testingTicker);

  private final JwtClaims jwtClaims1 = new JwtClaims();
  private final JwtClaims jwtClaims2 = new JwtClaims();

  @Test
  public void testDecodeWithCache() {
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims1);

    assertEquals(jwtClaims1, cachingDecoder.decode(AUTH_TOKEN));
    // Verify that the underlying decoder is called to load the cache.
    verify(authTokenDecoder, only()).decode(AUTH_TOKEN);

    reset(authTokenDecoder);
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims2);

    // Advance the ticker by 1 minute and make sure that the auth token is correctly cached.
    testingTicker.advance(TimeUnit.MINUTES.toSeconds(1));
    assertEquals(jwtClaims1, cachingDecoder.decode(AUTH_TOKEN));
    verify(authTokenDecoder, never()).decode(AUTH_TOKEN);

    // Advance the ticker by 5 minute to cause the cached entry to expire.
    testingTicker.advance(TimeUnit.MINUTES.toSeconds(5));
    assertEquals(jwtClaims2, cachingDecoder.decode(AUTH_TOKEN));
    verify(authTokenDecoder, only()).decode(AUTH_TOKEN);
  }
}
