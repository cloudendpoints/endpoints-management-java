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
import static org.junit.Assert.assertFalse;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Tests for {@link DefaultKeyUriSupplier}.
 *
 * @author yangguan@google.com
 *
 */
public final class DefautKeyUriSupplierTest {
  private static final ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

  private static final String ISSUER_A = "https://issuer-a.com";
  private static final String ISSUER_B = "http://issuer-b.com";
  private static final String ISSUER_C = "issuer-c.com";

  private static final GenericUrl JWKS_URI = new GenericUrl("https://jwks.uri");
  private static final GenericUrl OPENID_JWKS_URI = new GenericUrl("https://openid.jwks");
  private final IssuerKeyUrlConfig config1 = new IssuerKeyUrlConfig(false, Optional.of(JWKS_URI));
  private final IssuerKeyUrlConfig config2 =
      new IssuerKeyUrlConfig(true, Optional.<GenericUrl>absent());
  private final IssuerKeyUrlConfig config3 =
      new IssuerKeyUrlConfig(true, Optional.<GenericUrl>absent());

  private final Map<String, String> metadata = ImmutableMap.of("jwks_uri", OPENID_JWKS_URI.build());

  private final Map<String, IssuerKeyUrlConfig> configs = ImmutableMap.of(
      ISSUER_A, config1,
      ISSUER_B, config2,
      ISSUER_C, config3);

  @Test
  public void testSupplyIssuer() {
    HttpRequestFactory requestFactory = new TestingHttpTransport().createRequestFactory();
    KeyUriSupplier keyUriSupplier = new DefaultKeyUriSupplier(requestFactory, configs);
    assertEquals(JWKS_URI, keyUriSupplier.supply(ISSUER_A).get());
    // Test supplying for non-existent issuer.
    assertFalse(keyUriSupplier.supply("random-issuer").isPresent());
  }

  @Test
  public void testOpenIdDiscovery() throws JsonProcessingException {
    String metadatJson = OBJECT_WRITER.writeValueAsString(metadata);
    TestingHttpTransport httpTransport =
        new TestingHttpTransport(ISSUER_B + "/.well-known/openid-configuration", metadatJson);
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
    KeyUriSupplier keyUriSupplier = new DefaultKeyUriSupplier(requestFactory, configs);

    assertEquals(OPENID_JWKS_URI, keyUriSupplier.supply(ISSUER_B).get());
    assertEquals(1, httpTransport.getCallCount());
  }

  @Test
  public void testAutoSetProtocol() throws JsonProcessingException {
    String metadatJson = OBJECT_WRITER.writeValueAsString(metadata);
    TestingHttpTransport httpTransport =
        new TestingHttpTransport(
            "https://" + ISSUER_C + "/.well-known/openid-configuration",
            metadatJson);
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
    KeyUriSupplier keyUriSupplier = new DefaultKeyUriSupplier(requestFactory, configs);

    assertEquals(OPENID_JWKS_URI, keyUriSupplier.supply(ISSUER_C).get());
    assertEquals(1, httpTransport.getCallCount());
  }

  @Test
  public void testOpenIdDiscoveryWithInvalidResponse() {
    TestingHttpTransport httpTransport =
        new TestingHttpTransport(ISSUER_B + "/.well-known/openid-configuration", "--");
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
    KeyUriSupplier keyUriSupplier = new DefaultKeyUriSupplier(requestFactory, configs);

    try {
      keyUriSupplier.supply(ISSUER_B);
    } catch (UnauthenticatedException exception) {
      assertEquals("Cannot retrieve or parse OpenId Provider Metadata", exception.getMessage());
    }
  }

  @Test
  public void testInvalidOpenId() {
    String issuer = "issuer.com";
    IssuerKeyUrlConfig config = new IssuerKeyUrlConfig(false, Optional.<GenericUrl>absent());
    TestingHttpTransport testingHttpTransport = new TestingHttpTransport();
    HttpRequestFactory requestFactory = testingHttpTransport.createRequestFactory();
    KeyUriSupplier keyUriSupplier =
        new DefaultKeyUriSupplier(requestFactory, ImmutableMap.of(issuer, config));

    keyUriSupplier.supply(issuer);
    assertEquals(0, testingHttpTransport.getCallCount());
  }

  private static final class TestingHttpTransport extends MockHttpTransport {
    private int callCount;

    private final String expectedUrl;
    private final String jsonResponse;

    public TestingHttpTransport() {
      this("", "");
    }

    public TestingHttpTransport(String expectedUrl, String jsonResponse) {
      this.callCount = 0;
      this.expectedUrl = expectedUrl;
      this.jsonResponse = jsonResponse;
    }

    int getCallCount() {
      return this.callCount;
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      callCount ++;

      if (!HttpMethods.GET.equals(method) || !expectedUrl.equals(url)) {
        // Throw RuntimeException to fail the test.
        throw new RuntimeException();
      }

      return new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
          response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
          response.setContentType(Json.MEDIA_TYPE);
          response.setContent(jsonResponse);
          return response;
        }
      };
    }
  }
}
