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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.lang.JoseException;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link DefaultAuthTokenDecoder}.
 *
 * @author yangguan@google.com
 *
 */
public class DefaultAuthTokenDecoderTest {

  private static final List<String> AUDIENCES = ImmutableList.of("aud1", "aud2");
  private static final Optional<String> EMAIL = Optional.of("issuer@issuer.com");
  private static final Optional<String> SUBJECT = Optional.of("subject-id");
  private static final Optional<String> ISSUER = Optional.of("https://issuer.com");

  private final RsaJsonWebKey rsaJsonWebKey = TestUtils.generateRsaJsonWebKey("key-id");
  private final AuthTokenVerifier authTokenVerifier = mock(AuthTokenVerifier.class);
  private final AuthTokenDecoder authTokenDecoder = new DefaultAuthTokenDecoder(authTokenVerifier);

  @Test
  public void testDecodeSuccess() throws JoseException, MalformedClaimException {
    when(authTokenVerifier.verify(anyString(), anyString())).thenReturn(true);
    String authToken = TestUtils.generateAuthToken(
        Optional.<Collection<String>>of(AUDIENCES),
        EMAIL,
        ISSUER,
        SUBJECT,
        rsaJsonWebKey);
    JwtClaims jwtClaims = this.authTokenDecoder.decode(authToken);
    assertEquals(AUDIENCES, jwtClaims.getAudience());
    assertEquals(EMAIL.get(), jwtClaims.getStringClaimValue("email"));
    assertEquals(SUBJECT.get(), jwtClaims.getSubject());
    assertEquals(ISSUER.get(), jwtClaims.getIssuer());
  }

  @Test
  public void testDecodeUnverifiedAuthToken() throws JoseException {
    when(authTokenVerifier.verify(anyString(), anyString())).thenReturn(false);
    String authToken = TestUtils.generateAuthToken(
        Optional.<Collection<String>>of(AUDIENCES),
        EMAIL,
        ISSUER,
        SUBJECT,
        rsaJsonWebKey);
    try {
      this.authTokenDecoder.decode(authToken);
      fail();
    } catch (UnauthenticatedException exception) {
      assertEquals("Failed to verify the signature of the auth token", exception.getMessage());
    }
  }
}
