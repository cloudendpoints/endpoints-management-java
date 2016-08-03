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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Set;

/**
 * Holds the authentication results.
 *
 * @author yangguan@google.com
 *
 */
public class UserInfo {
  private final Set<String> audiences;
  private final String email;
  private final String id;
  private final String issuer;

  /**
   * Constructor.
   */
  public UserInfo(Collection<String> audiences, String email, String id, String issuer) {
    Preconditions.checkNotNull(audiences);
    Preconditions.checkNotNull(email);
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(issuer);

    this.audiences = ImmutableSet.copyOf(audiences);
    this.email = email;
    this.id = id;
    this.issuer = issuer;
  }

  public Set<String> getAudiences() {
    return audiences;
  }

  public String getEmail() {
    return email;
  }

  public String getId() {
    return id;
  }

  public String getIssuer() {
    return issuer;
  }
}
