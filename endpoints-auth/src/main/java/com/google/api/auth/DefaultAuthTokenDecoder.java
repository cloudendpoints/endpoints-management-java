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

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

/**
 * Default implementation of {@link AuthTokenDecoder}.
 *
 * @author yangguan@google.com
 *
 */
public class DefaultAuthTokenDecoder implements AuthTokenDecoder {

  private final AuthTokenVerifier authTokenVerifier;
  private final JwtConsumer jwtConsumer;

  /**
   * Constructor.
   *
   * @param authTokenVerifier is an instance of {@link AuthTokenDecoder} that
   *     verifies the signatures of auth tokens.
   */
  public DefaultAuthTokenDecoder(AuthTokenVerifier authTokenVerifier) {
    Preconditions.checkNotNull(authTokenVerifier);

    this.authTokenVerifier = authTokenVerifier;
    this.jwtConsumer = new JwtConsumerBuilder()
        .setDisableRequireSignature()
        .setSkipAllValidators()
        .setSkipSignatureVerification()
        .build();
  }

  @Override
  public JwtClaims decode(String authToken) {
    Preconditions.checkNotNull(authToken);

    try {
      JwtClaims jwtClaims = this.jwtConsumer.process(authToken).getJwtClaims();
      if (!this.authTokenVerifier.verify(authToken, jwtClaims.getIssuer())) {
        throw new UnauthenticatedException("Failed to verify the signature of the auth token");
      }
      return jwtClaims;
    } catch (InvalidJwtException | MalformedClaimException exception) {
      throw new UnauthenticatedException(exception);
    }
  }

}
