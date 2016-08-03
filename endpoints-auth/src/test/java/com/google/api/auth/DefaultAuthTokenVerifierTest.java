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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DefaultAuthTokenVerifier}.
 *
 * @author yangguan@google.com
 *
 */
public class DefaultAuthTokenVerifierTest {
  private static final int BITS_OF_KEY = 2048;
  private static final String ISSUER = "issuer";

  private RsaJsonWebKey rsaJwk1;
  private RsaJsonWebKey rsaJwk2;

  private final JwksSupplier jwksSupplier = mock(JwksSupplier.class);
  private final DefaultAuthTokenVerifier verifier = new DefaultAuthTokenVerifier(jwksSupplier);

  /**
   * Initialize the JSON web keys before each test.
   */
  @Before
  public void setUp() throws JoseException {
    this.rsaJwk1 = RsaJwkGenerator.generateJwk(BITS_OF_KEY);
    this.rsaJwk1.setKeyId("rsa-jwk-1");
    this.rsaJwk2 = RsaJwkGenerator.generateJwk(BITS_OF_KEY);
    this.rsaJwk2.setKeyId("rsa-jwk-2");
  }

  @Test
  public void testVerifyJwtWithoutKeyId() throws JoseException {
    when(jwksSupplier.supply(ISSUER)).thenReturn(new JsonWebKeySet(rsaJwk1));
    assertTrue(this.verifier.verify(generateJwt(rsaJwk1, Optional.<String>absent()), ISSUER));
  }

  @Test
  public void testVerifyJwtWithCorrectKeyId() throws JoseException {
    when(jwksSupplier.supply(ISSUER)).thenReturn(new JsonWebKeySet(rsaJwk1));
    assertTrue(this.verifier.verify(generateJwt(rsaJwk1, Optional.of(rsaJwk1.getKeyId())), ISSUER));
  }

  @Test
  public void testVerifyJwtWithWrongKeyId() throws JoseException {
    when(jwksSupplier.supply(ISSUER)).thenReturn(new JsonWebKeySet(rsaJwk1));
    assertFalse(this.verifier.verify(generateJwt(rsaJwk1, Optional.of("random-key-id")), ISSUER));
  }

  @Test
  public void testVerifyJwtSuccessWithMultipleCandidateKeys() throws JoseException {
    when(jwksSupplier.supply(ISSUER)).thenReturn(new JsonWebKeySet(rsaJwk1, rsaJwk2));
    assertTrue(this.verifier.verify(generateJwt(rsaJwk1, Optional.<String>absent()), ISSUER));
  }

  private static String generateJwt(RsaJsonWebKey jwk, Optional<String> keyId)
      throws JoseException {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer("Issuer");
    claims.setAudience("Audience");

    JsonWebSignature jws = new JsonWebSignature();
    jws.setPayload(claims.toJson());
    jws.setKey(jwk.getPrivateKey());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

    if (keyId.isPresent()) {
      jws.setKeyIdHeaderValue(keyId.get());
    }

    return jws.getCompactSerialization();
  }
}
