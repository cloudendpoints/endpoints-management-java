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

import com.google.common.base.Preconditions;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.VerificationJwkSelector;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

/**
 * Default implementation of {@link AuthTokenVerifier}.
 *
 * @author yangguan@google.com
 *
 */
public final class DefaultAuthTokenVerifier implements AuthTokenVerifier {

  private final VerificationJwkSelector jwkSelector;
  private final JwksSupplier jwksSupplier;

  public DefaultAuthTokenVerifier(JwksSupplier jwksSupplier) {
    this.jwkSelector = new VerificationJwkSelector();
    this.jwksSupplier = Preconditions.checkNotNull(jwksSupplier);
  }

  @Override
  public boolean verify(String authToken, String issuer) {
    Preconditions.checkNotNull(authToken);
    Preconditions.checkNotNull(issuer);

    try {
      JsonWebKeySet jwks = this.jwksSupplier.supply(issuer);
      JsonWebSignature jws = new JsonWebSignature();
      jws.setCompactSerialization(authToken);

      for (JsonWebKey jwk : this.jwkSelector.selectList(jws, jwks.getJsonWebKeys())) {
        jws.setKey(jwk.getKey());
        if (jws.verifySignature()) {
          return true;
        }
      }
    } catch (JoseException exception) {
      throw new UnauthenticatedException("Cannot verify the signature", exception);
    }
    return false;
  }
}
