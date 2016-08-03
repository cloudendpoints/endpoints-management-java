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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.AuthProvider;
import com.google.api.Authentication;
import com.google.api.client.util.Clock;
import com.google.api.control.model.MethodRegistry.AuthInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;

import org.jose4j.jwt.JwtClaims;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Tests for {@link Authenticator}.
 *
 * @author yangguan@google.com
 *
 */
public class AuthenticatorTest {
  private static final List<String> AUDIENCES = ImmutableList.of("aud1", "aud2");
  private static final String AUTH_TOKEN = "auth-token";
  private static final String EMAIL = "email@test.com";
  private static final int EXPIRATION_IN_FUTURE = 5;
  private static final String ID = "id";
  private static final String ISSUER = "issuer";
  private static final String PROVIDER_ID = "provider-id";
  private static final int NOT_BEFORE_IN_PAST = 0;
  private static final String SERVICE_NAME = "service-name";

  private final Map<String, String> issuersToProviderIds =
      ImmutableMap.<String, String>of(ISSUER, PROVIDER_ID);
  private final AuthTokenDecoder authTokenDecoder = mock(AuthTokenDecoder.class);
  private final Authenticator authenticator =
      new Authenticator(authTokenDecoder, Clock.SYSTEM, issuersToProviderIds);

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final JwtClaims jwtClaims =
      createJwtClaims(AUDIENCES, EMAIL, EXPIRATION_IN_FUTURE, ID, NOT_BEFORE_IN_PAST, ISSUER);
  private final UserInfo userInfo = new UserInfo(AUDIENCES, EMAIL, ID, ISSUER);

  @Before
  public void setUp() {
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims);
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + AUTH_TOKEN);
  }

  @Test
  public void testAuthenticate() {
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("aud1")));
    assertUserInfoEquals(userInfo, authenticator.authenticate(request, authInfo, SERVICE_NAME));
  }

  @Test
  public void testServiceNameAsAudience() {
    JwtClaims jwtClaims1 = createJwtClaims(
        ImmutableList.of(SERVICE_NAME),
        EMAIL,
        EXPIRATION_IN_FUTURE,
        ID,
        NOT_BEFORE_IN_PAST,
        ISSUER);
    UserInfo userInfo1 = new UserInfo(ImmutableList.of(SERVICE_NAME), EMAIL, ID, ISSUER);
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("aud1")));
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims1);
    assertUserInfoEquals(userInfo1, authenticator.authenticate(request, authInfo, SERVICE_NAME));
  }

  @Test
  public void testUnknownIssuer() {
    JwtClaims jwtClaims =
        createJwtClaims(AUDIENCES, EMAIL, EXPIRATION_IN_FUTURE, ID, NOT_BEFORE_IN_PAST, "random-issuer");
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims);
    AuthInfo authInfo = new AuthInfo(ImmutableMap.<String, Set<String>>of());
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      assertEquals("Unknown issuer: random-issuer", exception.getMessage());
    }
  }

  @Test
  public void testAuthenticateWithoutAllowedAudience() {
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("random-aud")));
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      assertEquals("Audiences not allowed", exception.getMessage());
    }
  }

  @Test
  public void testBadBearerToken() {
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("bad bearer token");
    AuthInfo authInfo = new AuthInfo(ImmutableMap.<String, Set<String>>of());
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      // expected
    }
  }

  @Test
  public void testAuthTokenAsRequestParameter() {
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
    when(request.getParameter("access_token")).thenReturn(AUTH_TOKEN);
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("aud1")));
    assertUserInfoEquals(userInfo, authenticator.authenticate(request, authInfo, SERVICE_NAME));
  }

  @Test
  public void testNoAuthToken() {
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
    when(request.getParameter("access_token")).thenReturn(null);
    AuthInfo authInfo = new AuthInfo(ImmutableMap.<String, Set<String>>of());
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      // expected
    }
  }

  @Test
  public void testExpiredAuthToken() {
    JwtClaims jwtClaims1 =
        createJwtClaims(ImmutableList.of(SERVICE_NAME), EMAIL, -1, ID, NOT_BEFORE_IN_PAST, ISSUER);
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("aud1")));
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims1);
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      assertEquals("The auth token has already expired", exception.getMessage());
    }
  }

  @Test
  public void testAuthTokenWithNotBefore() {
    JwtClaims jwtClaims1 = createJwtClaims(
        ImmutableList.of(SERVICE_NAME),
        EMAIL,
        EXPIRATION_IN_FUTURE,
        ID,
        -1,
        ISSUER);
    AuthInfo authInfo =
        new AuthInfo(ImmutableMap.<String, Set<String>>of(PROVIDER_ID, ImmutableSet.of("aud1")));
    when(authTokenDecoder.decode(AUTH_TOKEN)).thenReturn(jwtClaims1);
    try {
      authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail();
    } catch (UnauthenticatedException exception) {
      assertEquals("Current time is earlier than the \"nbf\" time", exception.getMessage());
    }
  }

  @Test
  public void testCreateWithoutAuthProviders() {
    try {
      Authenticator.create(Authentication.newBuilder().build(), Clock.SYSTEM);
      fail();
    } catch (IllegalArgumentException exception) {
      assertEquals("No auth providers are defined in the config.", exception.getMessage());
    }
  }

  @Test
  public void testCreateAuthenticator() {
    AuthProvider authProvider = AuthProvider.newBuilder()
        .setIssuer("https://issuer.com")
        .build();
    Authentication authentication = Authentication.newBuilder()
        .addProviders(authProvider)
        .build();
    Authenticator.create(authentication, Clock.SYSTEM);
  }

  @Test
  public void testCreateWithSameIssuers() {
    AuthProvider authProvider = AuthProvider.newBuilder()
        .setIssuer("https://issuer.com")
        .build();
    Authentication authentication = Authentication.newBuilder()
        .addProviders(authProvider)
        .addProviders(authProvider)
        .build();
    try {
      Authenticator.create(authentication, Clock.SYSTEM);
      fail();
    } catch (IllegalArgumentException exception) {
      String message = "Configuration contains multiple auth provider for the same issuer: "
          + authProvider.getIssuer();
      assertEquals(message, exception.getMessage());
    }
  }

  @Test
  public void testDisallowedProviderId() {
    AuthInfo authInfo = new AuthInfo(ImmutableMap.<String, Set<String>>of());
    try {
      this.authenticator.authenticate(request, authInfo, SERVICE_NAME);
      fail("Expected UnauthenticatedException.");
    } catch (UnauthenticatedException exception) {
      assertEquals(
          "The requested method does not allowed this provider id: " + PROVIDER_ID,
          exception.getMessage());
    }
  }

  private static void assertUserInfoEquals(UserInfo expected, UserInfo actual) {
    assertEquals(expected.getAudiences(), actual.getAudiences());
    assertEquals(expected.getEmail(), actual.getEmail());
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getIssuer(), actual.getIssuer());
  }

  private static JwtClaims createJwtClaims(
      List<String> audiences,
      String email,
      int expiration,
      String subject,
      int notBefore,
      String issuer) {

    JwtClaims jwtClaims = new JwtClaims();
    jwtClaims.setAudience(audiences);
    jwtClaims.setExpirationTimeMinutesInTheFuture(expiration);
    jwtClaims.setClaim("email", email);
    jwtClaims.setSubject(subject);
    jwtClaims.setNotBeforeMinutesInThePast(notBefore);
    jwtClaims.setIssuer(issuer);
    return jwtClaims;
  }
}
