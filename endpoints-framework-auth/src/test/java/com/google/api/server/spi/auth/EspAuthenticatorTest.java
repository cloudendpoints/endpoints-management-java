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

package com.google.api.server.spi.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.Service;
import com.google.api.auth.Authenticator;
import com.google.api.auth.UnauthenticatedException;
import com.google.api.auth.UserInfo;
import com.google.api.control.ConfigFilter;
import com.google.api.control.model.MethodRegistry.AuthInfo;
import com.google.api.control.model.MethodRegistry.Info;
import com.google.api.control.model.MethodRegistry.QuotaInfo;
import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.request.Attribute;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Test for {@link EspAuthenticator}.
 */
@RunWith(MockitoJUnitRunner.class)
public class EspAuthenticatorTest {
  private static final String ATTRIBUTE_PREFIX = ConfigFilter.class.getName();
  private static final String SERVICE_NAME = "service_name";
  private static final Service SERVICE = Service.newBuilder().setName(SERVICE_NAME).build();

  private static final AuthInfo AUTH_INFO = new AuthInfo(ImmutableMap.<String, Set<String>>of());
  private static final Info INFO = new Info("selector", AUTH_INFO, QuotaInfo.DEFAULT);

  @Mock private Authenticator authenticator;

  private Attribute attribute;
  private EspAuthenticator espAuthenticator;
  private HttpServletRequest request;

  @Before
  public void setUp() {
    this.espAuthenticator = new EspAuthenticator(authenticator);
    this.request = new MockHttpServletRequest();
    this.attribute = Attribute.from(request);
  }

  @Test
  public void testNoMethodInfo() {
    try {
      this.espAuthenticator.authenticate(request);
      fail("Expected IllegalStateException.");
    } catch (IllegalStateException exception) {
      assertEquals("method_info is not set in the request", exception.getMessage());
    }
  }

  @Test
  public void testNoService() {
    this.attribute.set(ATTRIBUTE_PREFIX + ".method_info", INFO);
    try {
      espAuthenticator.authenticate(request);
      fail("Expected IllegalStateException.");
    } catch (IllegalStateException exception) {
      assertEquals("service is not set in the request", exception.getMessage());
    }
  }

  @Test
  public void testAuthenticateSuccess() {
    String email = "user@email.com";
    String id = "user-id";
    UserInfo userInfo = new UserInfo(ImmutableList.<String>of(), email, id, "issuer");
    when(this.authenticator.authenticate(request, AUTH_INFO, SERVICE_NAME))
        .thenReturn(userInfo);
    this.attribute.set(ATTRIBUTE_PREFIX + ".method_info", INFO);
    this.attribute.set(ATTRIBUTE_PREFIX + ".service", SERVICE);

    User user = this.espAuthenticator.authenticate(request);
    assertEquals(email, user.getEmail());
    assertEquals(id, user.getId());

    verify(this.authenticator, only()).authenticate(request, AUTH_INFO, SERVICE_NAME);
  }

  @Test
  public void testNoAuthInfo() {
    Info info = new Info("selector", null, QuotaInfo.DEFAULT);
    this.attribute.set(ATTRIBUTE_PREFIX + ".method_info", info);
    assertNull(this.espAuthenticator.authenticate(request));
    verify(this.authenticator, never())
        .authenticate(any(HttpServletRequest.class), any(AuthInfo.class), anyString());
  }

  @Test
  public void testHandleUnauthenticatedException() {
    when(this.authenticator.authenticate(eq(request), any(AuthInfo.class), anyString()))
        .thenThrow(new UnauthenticatedException());
    this.attribute.set(ATTRIBUTE_PREFIX + ".method_info", INFO);
    this.attribute.set(ATTRIBUTE_PREFIX + ".service", SERVICE);
    assertNull(this.espAuthenticator.authenticate(request));
  }

  @Test
  public void testHandleUncheckedExecutionException() {
    when(this.authenticator.authenticate(eq(request), any(AuthInfo.class), anyString()))
        .thenThrow(new UncheckedExecutionException(new UnauthenticatedException()));
    this.attribute.set(ATTRIBUTE_PREFIX + ".method_info", INFO);
    this.attribute.set(ATTRIBUTE_PREFIX + ".service", SERVICE);
    assertNull(this.espAuthenticator.authenticate(request));
  }
}
