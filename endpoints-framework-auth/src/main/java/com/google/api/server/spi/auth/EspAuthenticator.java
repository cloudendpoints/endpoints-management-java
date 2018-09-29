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

import com.google.api.Service;
import com.google.api.auth.Authenticator;
import com.google.api.auth.UnauthenticatedException;
import com.google.api.auth.UserInfo;
import com.google.api.control.ConfigFilter;
import com.google.api.control.model.MethodRegistry.AuthInfo;
import com.google.api.control.model.MethodRegistry.Info;
import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Singleton;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.UncheckedExecutionException;
import javax.servlet.http.HttpServletRequest;

/**
 * Authenticator that extracts auth token from the HTTP authorization header or
 * from the "access_token" query parameter.
 *
 * This authenticator supports the same authentication feature as in Endpoints
 * Server Proxy.
 *
 * This authenticator needs to be placed behind {@link ConfigFilter} which adds
 * {@link Info} and {@link Service} as attributes of the incoming HTTP requests.
 */
@Singleton
public final class EspAuthenticator implements com.google.api.server.spi.config.Authenticator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Authenticator authenticator;

  public EspAuthenticator() {
    this(Authenticator.create());
  }

  @VisibleForTesting
  EspAuthenticator(Authenticator authenticator) {
    this.authenticator = authenticator;
  }


  @Override
  public User authenticate(HttpServletRequest request) {
    Info methodInfo = ConfigFilter.getMethodInfo(request);
    if (methodInfo == null) {
      throw new IllegalStateException("method_info is not set in the request");
    }
    Optional<AuthInfo> authInfo = methodInfo.getAuthInfo();
    if (!authInfo.isPresent()) {
      logger.atInfo().log("auth is not configured for this request");
      return null;
    }

    Service service = ConfigFilter.getService(request);
    if (service == null) {
      throw new IllegalStateException("service is not set in the request");
    }

    String serviceName = service.getName();

    try {
      UserInfo userInfo = this.authenticator.authenticate(request, authInfo.get(), serviceName);
      return new User(userInfo.getId(), userInfo.getEmail());
    } catch (UnauthenticatedException | UncheckedExecutionException exception) {
      logger.atWarning().withCause(exception).log("Authentication failed");
      return null;
    }
  }
}
