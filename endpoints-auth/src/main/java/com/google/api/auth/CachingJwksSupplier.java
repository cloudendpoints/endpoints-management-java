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

import com.google.api.client.util.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jose4j.jwk.JsonWebKeySet;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A {@link JwksSupplier} that caches results and delegates actual JWKS
 * retrieval to another {@link JwksSupplier}.
 *
 * @author yangguan@google.com
 *
 */
public final class CachingJwksSupplier implements JwksSupplier {
  private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(5);

  private final LoadingCache<String, JsonWebKeySet> jwksCache;

  public CachingJwksSupplier(JwksSupplier jwksSupplier) {
    this(Preconditions.checkNotNull(jwksSupplier), Ticker.systemTicker());
  }

  @VisibleForTesting
  CachingJwksSupplier(JwksSupplier jwksSupplier, Ticker ticker) {
    this.jwksCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_EXPIRATION.toMinutes(), TimeUnit.MINUTES)
        .ticker(ticker)
        .build(new JwksCacheLoader(jwksSupplier));
  }

  @Override
  public JsonWebKeySet supply(String issuer) {
    Preconditions.checkNotNull(issuer);

    // Use getUncheck(token) instead of get(token) since we know the loading
    // method (DefaultCacheLoader#load) does not throw checked exception.
    return this.jwksCache.getUnchecked(issuer);
  }

  private static final class JwksCacheLoader extends CacheLoader<String, JsonWebKeySet> {
    private final JwksSupplier jwksSupplier;

    JwksCacheLoader(JwksSupplier jwksSupplier) {
      this.jwksSupplier = jwksSupplier;
    }

    @Override
    public JsonWebKeySet load(String issuer) throws Exception {
      return this.jwksSupplier.supply(issuer);
    }
  }
}
