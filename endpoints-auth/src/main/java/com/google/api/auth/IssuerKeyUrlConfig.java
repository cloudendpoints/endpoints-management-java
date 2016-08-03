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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * The key url configuration for a given issuer.
 *
 * @author yangguan@google.com
 *
 */
public class IssuerKeyUrlConfig {
  private final boolean openIdValid;
  private final Optional<GenericUrl> jwksUri;

  /**
   * Constructor.
   *
   * @param openIdValid indicates whether the corresponding issuer is valid for OpenId discovery.
   * @param jwksUri is the saved jwks_uri. Its value can be Optional.absent() if the OpenId
   *     discovery process has not begun or has already failed.
   */
  public IssuerKeyUrlConfig(boolean openIdValid, Optional<GenericUrl> jwksUri) {
    Preconditions.checkNotNull(jwksUri);

    this.openIdValid = openIdValid;
    this.jwksUri = jwksUri;
  }

  public boolean isOpenIdValid() {
    return this.openIdValid;
  }

  public Optional<GenericUrl> getJwksUri() {
    return this.jwksUri;
  }
}
