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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The default implementation of {@JwksSupplier}.
 *
 * @author yangguan@google.com
 *
 */
public class DefaultJwksSupplier implements JwksSupplier {
  @VisibleForTesting
  static final String X509_CERT_PREFIX = "-----BEGIN CERTIFICATE-----";
  @VisibleForTesting
  static final String X509_CERT_SUFFIX = "-----END CERTIFICATE-----";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final HttpRequestFactory httpRequestFactory;
  private final KeyUriSupplier keyUriSupplier;

  public DefaultJwksSupplier(HttpRequestFactory httpRequestFactory, KeyUriSupplier keyUriSupplier) {
    this.httpRequestFactory = Preconditions.checkNotNull(httpRequestFactory);
    this.keyUriSupplier = Preconditions.checkNotNull(keyUriSupplier);
  }

  @Override
  public JsonWebKeySet supply(String issuer) {
    Preconditions.checkNotNull(issuer);

    Optional<GenericUrl> jwksUri = this.keyUriSupplier.supply(issuer);
    if (!jwksUri.isPresent()) {
      String message = String.format("Cannot find the jwks_uri for issuer %s: "
          + "either the issuer is unknown or the OpenID discovery failed", issuer);
      throw new UnauthenticatedException(message);
    }
    String rawJson = retrieveJwksJson(jwksUri.get());

    Map<String, Object> rawMap = parse(rawJson, new TypeReference<Map<String, Object>>() {});
    if (rawMap.containsKey("keys")) {
      // De-serialize the JSON string as a JWKS object.
      return extractJwks(rawJson);
    }
    // Otherwise try to de-serialize the JSON string as a map mapping from key
    // id to X.509 certificate.
    return extractX509Certificate(rawJson);
  }

  private String retrieveJwksJson(GenericUrl jwksUri) {
    try {
      HttpResponse response = this.httpRequestFactory.buildGetRequest(jwksUri).execute();
      return response.parseAsString();
    } catch (IOException exception) {
      String message = String.format("Cannot retrive the JWKS json from %s", jwksUri.build());
      throw new UnauthenticatedException(message, exception);
    }
  }

  private static <T> T parse(String json, TypeReference<T> typeReference) {
    try {
      return OBJECT_MAPPER.readValue(json, typeReference);
    } catch (IOException exception) {
      throw new UnauthenticatedException("Cannot parse the JSON string", exception);
    }
  }

  private JsonWebKeySet extractX509Certificate(String json) {
    Map<String, String> certificates = parse(json, new TypeReference<Map<String, String>>() {});
    ImmutableList.Builder<JsonWebKey> jwkBuilder = ImmutableList.builder();
    X509Util x509Util = new X509Util();
    for (Entry<String, String> entry : certificates.entrySet()) {
      try {
        String cert = entry.getValue().trim()
            .replace(X509_CERT_PREFIX, "")
            .replace(X509_CERT_SUFFIX, "");
        X509Certificate x509Certificate = x509Util.fromBase64Der(cert);
        PublicKey publicKey = x509Certificate.getPublicKey();
        JsonWebKey jwk = toJsonWebKey(publicKey);
        jwk.setKeyId(entry.getKey());
        jwkBuilder.add(jwk);
      } catch (JoseException exception) {
        throw new UnauthenticatedException("Failed to parse public key", exception);
      }
    }
    return new JsonWebKeySet(jwkBuilder.build());
  }

  private static JsonWebKeySet extractJwks(String json) {
    try {
      return new JsonWebKeySet(json);
    } catch (JoseException exception) {
      throw new UnauthenticatedException("Cannot create a JsonWebKeySet");
    }
  }

  private static JsonWebKey toJsonWebKey(PublicKey publicKey) {
    if (publicKey instanceof RSAPublicKey) {
      return new RsaJsonWebKey((RSAPublicKey) publicKey);
    } else if (publicKey instanceof ECPublicKey) {
      return new EllipticCurveJsonWebKey((ECPublicKey) publicKey);
    }
    String message = "Unsupported public key type: " + publicKey.getClass().getSimpleName();
    throw new UnauthenticatedException(message);
  }
}
