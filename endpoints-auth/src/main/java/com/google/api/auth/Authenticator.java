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

import com.google.api.AuthProvider;
import com.google.api.Authentication;
import com.google.api.Service;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.Clock;
import com.google.api.client.util.Maps;
import com.google.api.config.ServiceConfigFetcher;
import com.google.api.control.model.MethodRegistry.AuthInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.ReservedClaimNames;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * An authenticator that extracts the auth token from the HTTP request and
 * constructs a {@link UserInfo} object based on the claims contained in the
 * auth token.
 *
 * @author yangguan@google.com
 *
 */
public class Authenticator {

  private static final Logger logger = Logger.getLogger(Authenticator.class.getName());

  private static final String ACCESS_TOKEN_PARAM_NAME = "access_token";
  private static final String BEARER_TOKEN_PREFIX = "Bearer ";
  private static final String EMAIL_CLAIM_NAME = "email";

  private final AuthTokenDecoder authTokenDecoder;
  private final Clock clock;
  private final Map<String, String> issuersToProviderIds;

  /**
   * Constructor.
   *
   * @param authTokenDecoder decodes auth tokens into {@link UserInfo} objects.
   * @param clock provides the time.
   * @param issuersToProviderIds maps from issuers to provider IDs.
   */
  @VisibleForTesting
  Authenticator(
      AuthTokenDecoder authTokenDecoder,
      Clock clock,
      Map<String, String> issuersToProviderIds) {
    this.authTokenDecoder = authTokenDecoder;
    this.clock = clock;
    this.issuersToProviderIds = issuersToProviderIds;
  }

  /**
   * Authenticate the current HTTP request.
   *
   * @param httpServletRequest is the incoming HTTP request object.
   * @param authInfo contains authentication configurations of the API method being called.
   * @param serviceName is the name of this service.
   * @return a constructed {@link UserInfo} object representing the identity of the caller.
   */
  public UserInfo authenticate(
      HttpServletRequest httpServletRequest,
      AuthInfo authInfo,
      String serviceName) {

    Preconditions.checkNotNull(httpServletRequest);
    Preconditions.checkNotNull(authInfo);

    Optional<String> maybeAuthToken = extractAuthToken(httpServletRequest);
    if (!maybeAuthToken.isPresent()) {
      throw new UnauthenticatedException(
          "No auth token is contained in the HTTP request");
    }

    JwtClaims jwtClaims = this.authTokenDecoder.decode(maybeAuthToken.get());
    UserInfo userInfo = toUserInfo(jwtClaims);
    String issuer = userInfo.getIssuer();

    if (!this.issuersToProviderIds.containsKey(issuer)) {
      throw new UnauthenticatedException("Unknown issuer: " + issuer);
    }
    String providerId = this.issuersToProviderIds.get(issuer);

    // Check whether the provider id is allowed.
    if (!authInfo.isProviderIdAllowed(providerId)) {
      String message = "The requested method does not allowed this provider id: " + providerId;
      throw new UnauthenticatedException(message);
    }

    checkJwtClaims(jwtClaims);

    // Check the audiences decoded from the auth token. The auth token is allowed when
    // 1) an audience is equal to the service name,
    // or 2) at least one audience is allowed in the method configuration.
    Set<String> audiences = userInfo.getAudiences();
    boolean hasServiceName = audiences.contains(serviceName);
    Set<String> allowedAudiences = authInfo.getAudiencesForProvider(providerId);
    if (!hasServiceName && Sets.intersection(audiences, allowedAudiences).isEmpty()) {
      throw new UnauthenticatedException("Audiences not allowed");
    }

    return userInfo;
  }

  // Check whether the JWT claims should be accepted.
  private void checkJwtClaims(JwtClaims jwtClaims) {
    Optional<NumericDate> expiration = getDateClaim(ReservedClaimNames.EXPIRATION_TIME, jwtClaims);
    if (!expiration.isPresent()) {
      throw new UnauthenticatedException("Missing expiration field");
    }
    Optional<NumericDate> notBefore = getDateClaim(ReservedClaimNames.NOT_BEFORE, jwtClaims);

    NumericDate currentTime = NumericDate.fromMilliseconds(clock.currentTimeMillis());
    if (expiration.get().isBefore(currentTime)) {
      throw new UnauthenticatedException("The auth token has already expired");
    }

    if (notBefore.isPresent() && notBefore.get().isAfter(currentTime)) {
      String message = "Current time is earlier than the \"nbf\" time";
      throw new UnauthenticatedException(message);
    }
  }

  /**
   * Create an instance of {@link Authenticator} using the service configuration fetched from Google
   * Service Management APIs.
   *
   * @return an {@code Authenticator}
   * @throws java.lang.IllegalArgumentException if the authentication message is not defined in the
   *         fetched service config.
   */
  public static Authenticator create() {
    ServiceConfigFetcher fetcher = ServiceConfigFetcher.create();
    Service service = fetcher.fetch();
    if (!service.hasAuthentication()) {
      throw new IllegalArgumentException("Authentication is not defined in service config");
    }
    return create(service.getAuthentication(), Clock.SYSTEM);
  }

  @VisibleForTesting
  static Authenticator create(Authentication authentication, Clock clock) {
    List<AuthProvider> providersList = authentication.getProvidersList();
    if (providersList == null || providersList.isEmpty()) {
      throw new IllegalArgumentException("No auth providers are defined in the config.");
    }
    Map<String, IssuerKeyUrlConfig> issuerKeyConfigs = generateIssuerKeyConfig(providersList);

    Map<String, String> issuersToProviderIds = Maps.newHashMap();
    for (AuthProvider authProvider : providersList) {
      issuersToProviderIds.put(authProvider.getIssuer(), authProvider.getId());
    }

    HttpRequestFactory httpRequestFactory = new NetHttpTransport().createRequestFactory();
    KeyUriSupplier defaultKeyUriSupplier =
        new DefaultKeyUriSupplier(httpRequestFactory, issuerKeyConfigs);
    JwksSupplier jwksSupplier = new DefaultJwksSupplier(httpRequestFactory, defaultKeyUriSupplier);
    JwksSupplier cachingJwksSupplier = new CachingJwksSupplier(jwksSupplier);
    AuthTokenVerifier authTokenVerifier = new DefaultAuthTokenVerifier(cachingJwksSupplier);
    AuthTokenDecoder authTokenDecoder = new DefaultAuthTokenDecoder(authTokenVerifier);
    AuthTokenDecoder cachingAuthTokenDecoder = new CachingAuthTokenDecoder(authTokenDecoder);

    return new Authenticator(
        cachingAuthTokenDecoder,
        clock,
        ImmutableMap.<String, String>copyOf(issuersToProviderIds));
  }

  private static Map<String, IssuerKeyUrlConfig> generateIssuerKeyConfig(
      List<AuthProvider> authProviders) {

    ImmutableMap.Builder<String, IssuerKeyUrlConfig> issuerConfigBuilder = ImmutableMap.builder();
    Set<String> issuers = Sets.newHashSet();
    for (AuthProvider authProvider : authProviders) {
      String issuer = authProvider.getIssuer();
      if (Strings.isNullOrEmpty(issuer)) {
        logger.warning(
            String.format("The 'issuer' field is not set in AuthProvider (%s)", authProvider));
        continue;
      }

      if (issuers.contains(issuer)) {
        throw new IllegalArgumentException(
            "Configuration contains multiple auth provider for the same issuer: " + issuer);
      }
      issuers.add(issuer);

      String jwksUri = authProvider.getJwksUri();
      IssuerKeyUrlConfig config = Strings.isNullOrEmpty(jwksUri)
          ? new IssuerKeyUrlConfig(true, Optional.<GenericUrl>absent())
          : new IssuerKeyUrlConfig(false, Optional.of(new GenericUrl(jwksUri)));
      issuerConfigBuilder.put(issuer, config);
    }
    return issuerConfigBuilder.build();
  }

  private static Optional<NumericDate> getDateClaim(String claimName, JwtClaims jwtClaims) {
    try {
      NumericDate dateClaim = jwtClaims.getNumericDateClaimValue(claimName);
      return Optional.fromNullable(dateClaim);
    } catch (MalformedClaimException exception) {
      String message = String.format("The \"%s\" claim is malformed", claimName);
      throw new UnauthenticatedException(message);
    }
  }

  private static Optional<String> extractAuthToken(HttpServletRequest request) {
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader != null) {
      // When the authorization header is present, extract the token from the
      // header.
      if (authHeader.startsWith(BEARER_TOKEN_PREFIX)) {
        return Optional.of(authHeader.substring(BEARER_TOKEN_PREFIX.length()));
      }
      return Optional.absent();
    }

    String accessToken = request.getParameter(ACCESS_TOKEN_PARAM_NAME);
    if (accessToken != null) {
      return Optional.of(accessToken);
    }

    return Optional.absent();
  }

  private static UserInfo toUserInfo(JwtClaims jwtClaims) {
    try {
      List<String> audiences = jwtClaims.getAudience();
      if (audiences == null || audiences.isEmpty()) {
        throw new UnauthenticatedException("Missing audience field");
      }

      String email = jwtClaims.getClaimValue(EMAIL_CLAIM_NAME, String.class);

      String subject = jwtClaims.getSubject();
      if (subject == null) {
        throw new UnauthenticatedException("Missing subject field");
      }

      String issuer = jwtClaims.getIssuer();
      if (issuer == null) {
        throw new UnauthenticatedException("Missing issuer field");
      }

      return new UserInfo(audiences, email, subject, issuer);
    } catch (MalformedClaimException exception) {
      throw new UnauthenticatedException("Cannot read malformed claim", exception);
    }
  }
}
