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

package com.google.api.auth.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

/**
 * Stores the mapping from issuers to allowed audiences for a API method.
 *
 * <p>TODO (yangguan) this class should be removed and defined by protocol
 * buffers instead.
 *
 * @author yangguan@google.com
 *
 */
public final class MethodInfo {

  private final Map<String, Set<String>> issuerAudiences;

  public MethodInfo(Map<String, Set<String>> issuerAudiences) {
    Preconditions.checkNotNull(issuerAudiences);
    this.issuerAudiences = ImmutableMap.copyOf(issuerAudiences);
  }

  public boolean isIssuerAllowed(String issuer) {
    Preconditions.checkNotNull(issuer);
    return this.issuerAudiences.containsKey(issuer);
  }

  public Set<String> getAudiencesForIssuer(String issuer) {
    Preconditions.checkNotNull(issuer);

    if (this.issuerAudiences.containsKey(issuer)) {
      return this.issuerAudiences.get(issuer);
    }
    return ImmutableSet.<String>of();
  }
}
