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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.http.HttpStatus;
import org.bouncycastle.util.encoders.Hex;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Tests for {@link DefaultJwksSupplier};
 *
 * @author yangguan@google.com
 *
 */
public final class DefaultJwksSupplierTest {
  private static final String ISSUER = "issuer";
  private static final GenericUrl URI = new GenericUrl("https://jwks.uri/");
  private static ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

  private final KeyUriSupplier keyUriSupplier = mock(KeyUriSupplier.class);

  @Before
  public void setUp() throws JoseException {
    when(this.keyUriSupplier.supply(ISSUER)).thenReturn(Optional.of(URI));
  }

  @Test
  public void testSupplyJwks() throws JoseException {
    RsaJsonWebKey rsaJwk = RsaJwkGenerator.generateJwk(2048);
    JsonWebKeySet jsonWebKeySet = new JsonWebKeySet(rsaJwk);
    HttpTransport httpTransport = new TestingHttpTransport(jsonWebKeySet.toJson(), null);
    DefaultJwksSupplier jwksSupplier =
        new DefaultJwksSupplier(httpTransport.createRequestFactory(), keyUriSupplier);
    List<JsonWebKey> jsonWebKeys = jwksSupplier.supply(ISSUER).getJsonWebKeys();
    JsonWebKey jsonWebKey = Iterables.getOnlyElement(jsonWebKeys);

    assertKeysEqual(rsaJwk.getKey(), jsonWebKey.getKey());
  }

  @Test
  public void testSupplyJwksFromX509Certificate() throws
      NoSuchAlgorithmException, JsonProcessingException {
    RsaJsonWebKey rsaJsonWebKey = TestUtils.generateRsaJsonWebKey("key-id");
    String cert = TestUtils.generateX509Cert(rsaJsonWebKey);
    String keyId = "key-id";
    String json = OBJECT_WRITER.writeValueAsString(ImmutableMap.of(keyId, cert));

    HttpTransport httpTransport = new TestingHttpTransport(json, null);
    DefaultJwksSupplier jwksSupplier =
        new DefaultJwksSupplier(httpTransport.createRequestFactory(), keyUriSupplier);
    JsonWebKeySet jsonWebKeySet = jwksSupplier.supply(ISSUER);
    JsonWebKey jsonWebKey = Iterables.getOnlyElement(jsonWebKeySet.getJsonWebKeys());

    assertEquals(keyId, jsonWebKey.getKeyId());
    assertKeysEqual(rsaJsonWebKey.getPublicKey(), jsonWebKey.getKey());
  }

  @Test
  public void testSupplyWithIoException() {
    HttpTransport httpTransport = new TestingHttpTransport(null, new IOException());
    DefaultJwksSupplier jwksSupplier =
        new DefaultJwksSupplier(httpTransport.createRequestFactory(), keyUriSupplier);
    try {
      jwksSupplier.supply(ISSUER);
    } catch (UnauthenticatedException exception) {
      assertTrue(exception.getCause() instanceof IOException);
    }
  }

  @Test
  public void testSupplyWithUnknownIssuer() {
    TestingHttpTransport httpTransport = new TestingHttpTransport(null, null);
    DefaultJwksSupplier jwksSupplier =
        new DefaultJwksSupplier(httpTransport.createRequestFactory(), keyUriSupplier);

    when(keyUriSupplier.supply(ISSUER)).thenReturn(Optional.<GenericUrl>absent());
    try {
      jwksSupplier.supply(ISSUER);
      fail();
    } catch (Exception exception) {
      assertTrue(
          exception.getMessage().startsWith("Cannot find the jwks_uri for issuer " + ISSUER));
    }
  }

  private static void assertKeysEqual(Key expected, Key actual) {
    assertEquals(expected.getAlgorithm(), actual.getAlgorithm());
    assertEquals(
        new String(Hex.encode(expected.getEncoded())),
        new String(Hex.encode(actual.getEncoded())));
    assertEquals(expected.getFormat(), actual.getFormat());
  }

  private static final class TestingHttpTransport extends MockHttpTransport {
    private final String content;
    private final IOException ioException;

    public TestingHttpTransport(String content, IOException exception) {
      this.content = content;
      this.ioException = exception;
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      return new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          if (ioException != null) {
            throw ioException;
          }
          MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
          response.setStatusCode(HttpStatus.SC_ACCEPTED);
          response.setContentType(Json.MEDIA_TYPE);
          response.setContent(content);
          return response;
        }
      };
    }
  }
}
