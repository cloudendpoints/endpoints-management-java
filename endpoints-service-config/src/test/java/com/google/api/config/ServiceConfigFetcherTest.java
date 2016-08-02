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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.Service;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests for {@link ServiceConfigFetcher}.
 */
public final class ServiceConfigFetcherTest {

  private static final String ACCESS_TOKEN = "test-access-token";
  private static final String SERVICE_NAME = "test-service-name";
  private static final String SERVICE_VERSION = "test-service-version";

  private static final Service SERVICE = Service.newBuilder()
      .setName(SERVICE_NAME)
      .setId(SERVICE_VERSION)
      .build();

  private final AppIdentityService mockAppIdentityService =
      mock(AppIdentityService.class, RETURNS_DEEP_STUBS);
  private final Environment mockEnvironment = mock(Environment.class);
  private final TestingHttpTransport testHttpTransport = new TestingHttpTransport();
  private final ServiceConfigFetcher fetcher = new ServiceConfigFetcher(
      mockAppIdentityService,
      mockEnvironment,
      testHttpTransport);

  @Before
  public void setUp() {
    when(mockAppIdentityService.getAccessToken(anyListOf(String.class)).getAccessToken())
        .thenReturn(ACCESS_TOKEN);
    testHttpTransport.reset();
  }

  @Test
  public void testServiceNameNotSet() throws IOException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn("");
    try {
      fetcher.fetch();
      fail();
    } catch (IllegalArgumentException exception) {
      assertEquals(
          "Environment variable 'ENDPOINTS_SERVICE_NAME' is not set",
          exception.getMessage());
    }
  }

  @Test
  public void testServiceVersionNotSet() throws IOException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn(SERVICE_NAME);
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_VERSION")).thenReturn(null);
    try {
      fetcher.fetch();
      fail();
    } catch (IllegalArgumentException exception) {
      assertEquals(
          "Environment variable 'ENDPOINTS_SERVICE_VERSION' is not set",
          exception.getMessage());
    }
  }

  @Test
  public void testFetchSuccessfully() throws InvalidProtocolBufferException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn(SERVICE_NAME);
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_VERSION")).thenReturn(SERVICE_VERSION);

    String content = JsonFormat.printer().print(SERVICE);
    testHttpTransport.setStatusCode(200);
    testHttpTransport.setContent(content);

    assertEquals(SERVICE, fetcher.fetch());
  }

  @Test
  public void testFetchConfigWithWrongServiceName() throws InvalidProtocolBufferException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn(SERVICE_NAME);
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_VERSION")).thenReturn(SERVICE_VERSION);

    Service service = Service.newBuilder().setName("random-name").build();
    String content = JsonFormat.printer().print(service);
    testHttpTransport.setStatusCode(200);
    testHttpTransport.setContent(content);

    try {
      fetcher.fetch();
      fail();
    } catch (ServiceConfigException exception) {
      assertEquals("Unexpected service name in service config: random-name", exception.getMessage());
    }
  }

  @Test
  public void testFetchConfigWithWrongServiceVersion() throws InvalidProtocolBufferException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn(SERVICE_NAME);
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_VERSION")).thenReturn(SERVICE_VERSION);

    Service service = Service.newBuilder()
        .setName(SERVICE_NAME)
        .setId("random-version")
        .build();
    String content = JsonFormat.printer().print(service);
    testHttpTransport.setStatusCode(200);
    testHttpTransport.setContent(content);

    try {
      fetcher.fetch();
      fail();
    } catch (ServiceConfigException exception) {
      assertEquals("Unexpected service version in service config: random-version", exception.getMessage());
    }
  }

  @Test
  public void testFetchFailed() throws IOException {
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_NAME")).thenReturn(SERVICE_NAME);
    when(mockEnvironment.getVariable("ENDPOINTS_SERVICE_VERSION")).thenReturn(SERVICE_VERSION);
    testHttpTransport.setStatusCode(404);

    try {
      fetcher.fetch();
      fail();
    } catch (ServiceConfigException exception) {
      assertEquals("Failed to fetch service config (status code 404)", exception.getMessage());
    }
  }

  private static final class TestingHttpTransport extends MockHttpTransport {
    private String content;
    private int statusCode;

    public void setContent(String content) {
      this.content = content;
    }

    public void setStatusCode(int statusCode) {
      this.statusCode = statusCode;
    }

    public void reset() {
      this.content = null;
      this.statusCode = HttpStatus.SC_OK;
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      return new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
          response.setStatusCode(statusCode);
          response.setContentType(Json.MEDIA_TYPE);
          response.setContent(content);
          return response;
        }
      };
    }
  }
}
