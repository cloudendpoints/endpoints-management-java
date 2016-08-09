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

package com.google.api.control;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.util.Clock;
import com.google.appengine.api.ThreadManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * {@code GoogleAppEngineControlFilter} is a {@code ControlFilter} where the control client is
 * configured for use on GoogleAppEngine.
 *
 * TODO: maybe move this into its own project com.google.endpoints
 * control-appengine ?.  Then endpoints users can pull that lib.
 *
 * {@link ControlFilter#createClient(String)} is overridden to specify Google App Engine default
 * values
 */
public class GoogleAppEngineControlFilter extends ControlFilter {
  @VisibleForTesting
  GoogleAppEngineControlFilter(Client client, String projectId, Ticker ticker, Clock clock) {
    super(client, projectId, ticker, clock);
  }

  /**
   * No-op constructor.
   *
   * This makes it easy to inject or configure using web.xml.
   */
  public GoogleAppEngineControlFilter() {}

  /**
   * {@inheritDoc}
   */
  @Override
  protected Client createClient(String configServiceName)
      throws GeneralSecurityException, IOException {
    return new Client.Builder(configServiceName)
        .setHttpTransport(UrlFetchTransport.getDefaultInstance())
        .setFactory(ThreadManager.backgroundThreadFactory())
        .build();
  }
}
