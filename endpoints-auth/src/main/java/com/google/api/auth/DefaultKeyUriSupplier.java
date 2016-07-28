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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link KeyUriSupplier}.
 *
 * @author yangguan@google.com
 *
 */
public final class DefaultKeyUriSupplier implements KeyUriSupplier {
  private static final String HTTPS_PROTOCOL_PREFIX = "https://";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String OPEN_ID_CONFIG_PATH = ".well-known/openid-configuration";

  private final HttpRequestFactory httpRequestFactory;
  private final Map<String, IssuerKeyUrlConfig> issuerKeyUrls;

  /**
   * Constructor.
   *
   * @param httpRequestFactory is the factory used to make HTTP requests.
   * @param issuerKeyUrls is the configurations that map an issuer to the verification key URL.
   */
  public DefaultKeyUriSupplier(
      HttpRequestFactory httpRequestFactory,
      Map<String, IssuerKeyUrlConfig> issuerKeyUrls) {
    Preconditions.checkNotNull(httpRequestFactory);
    Preconditions.checkNotNull(issuerKeyUrls);

    this.httpRequestFactory = httpRequestFactory;
    this.issuerKeyUrls = new ConcurrentHashMap<>(issuerKeyUrls);
  }

  @Override
  public Optional<GenericUrl> supply(String issuer) {
    Preconditions.checkNotNull(issuer);

    if (!this.issuerKeyUrls.containsKey(issuer)) {
      // The issuer is unknown.
      return Optional.absent();
    }

    IssuerKeyUrlConfig issuerKeyUrlConfig = this.issuerKeyUrls.get(issuer);
    Optional<GenericUrl> jwksUri = issuerKeyUrlConfig.getJwksUri();
    if (jwksUri.isPresent()) {
      // When jwksUri already exists, we return it directly.
      return jwksUri;
    }

    // When jwksUri is empty, we try to retrieve it through the OpenID discovery.
    boolean openIdValid = issuerKeyUrlConfig.isOpenIdValid();
    if (openIdValid) {
      // Start the OpenId discovery process only when openIdValid is true, i.e.
      // there is no previous failed attempt.
      Optional<GenericUrl> discoveredJwksUri = discoverJwksUri(issuer);
      // Update the record.
      this.issuerKeyUrls.put(issuer, new IssuerKeyUrlConfig(false, discoveredJwksUri));

      return discoveredJwksUri;
    }

    return Optional.absent();
  }

  private Optional<GenericUrl> discoverJwksUri(String issuer) {
    // Construct the discovery URI based on the issuer.
    String openIdUrl = constructOpenIdUrl(issuer);
    GenericUrl jwksUri = this.retrieveRemoteJwksUri(openIdUrl);
    return Optional.of(jwksUri);
  }

  private GenericUrl retrieveRemoteJwksUri(String openIdUrl) {
    try {
      GenericUrl genericUrl = new GenericUrl(openIdUrl);
      HttpResponse httpResponse = this.httpRequestFactory.buildGetRequest(genericUrl).execute();
      String json = httpResponse.parseAsString();
      ProviderMetadata metadata = OBJECT_MAPPER.readValue(json, ProviderMetadata.class);
      return new GenericUrl(metadata.getJwksUri());
    } catch (IOException exception) {
      throw new UnauthenticatedException("Cannot retrieve or parse OpenId Provider Metadata",
          exception);
    }
  }

  // Construct the OpenID discovery URL based on the issuer.
  private static String constructOpenIdUrl(String issuer) {
    String url = issuer;
    if (!URI.create(issuer).isAbsolute()) {
      // Use HTTPS if the protocol scheme is not specified in the URL.
      url = HTTPS_PROTOCOL_PREFIX + issuer;
    }
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + OPEN_ID_CONFIG_PATH;
  }

  private static final class ProviderMetadata {
    private final String jwksUri;

    @SuppressWarnings("unused")
    ProviderMetadata(@JsonProperty("jwks_uri") String jwksUri) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(jwksUri));

      this.jwksUri = jwksUri;
    }

    String getJwksUri() {
      return this.jwksUri;
    }
  }
}
