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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jose4j.jwt.JwtClaims;

import java.util.concurrent.TimeUnit;

/**
 * A {@link AuthTokenDecoder} that caches results and delegates actual token
 * decoding to another {@link AuthTokenDecoder}.
 *
 * @author yangguan@google.com
 */
public final class CachingAuthTokenDecoder implements AuthTokenDecoder {
  private static final int CACHE_CAPACITY = 200;
  private static final int CACHE_EXPIRATION_IN_MINUTES = 5;

  private final LoadingCache<String, JwtClaims> authTokenCache;

  public CachingAuthTokenDecoder(AuthTokenDecoder authTokenDecoder) {
    this(authTokenDecoder, Ticker.systemTicker());
  }

  @VisibleForTesting
  CachingAuthTokenDecoder(AuthTokenDecoder authTokenDecoder, Ticker ticker) {
    this.authTokenCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_CAPACITY)
        .expireAfterWrite(CACHE_EXPIRATION_IN_MINUTES, TimeUnit.MINUTES)
        .ticker(ticker)
        .build(new DefaultCacheLoader(authTokenDecoder));
  }

  @Override
  public JwtClaims decode(String authToken) {
    Preconditions.checkNotNull(authToken);

    // Use getUncheck(token) instead of get(token) since we know the loading
    // method (DefaultCacheLoader#load) does not throw checked exception.
    return this.authTokenCache.getUnchecked(authToken);
  }

  private static final class DefaultCacheLoader extends CacheLoader<String, JwtClaims> {
    private final AuthTokenDecoder authTokenDecoder;

    DefaultCacheLoader(AuthTokenDecoder authTokenDecoder) {
      this.authTokenDecoder = authTokenDecoder;
    }

    @Override
    public JwtClaims load(String authToken) throws Exception {
      return this.authTokenDecoder.decode(authToken);
    }
  }
}
