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

import com.google.api.Service;
import com.google.api.config.ServiceConfigFetcher;

import java.io.IOException;

/**
 * ServiceManagementConfigFilter is a {@link ConfigFilter} where the service definition is loaded
 * from the service management api.
 */
public class ServiceManagementConfigFilter extends ConfigFilter {
  private static final Loader LOADER = new Loader() {

    @Override
    public Service load() throws IOException {
      return ServiceConfigFetcher.create().fetch();
    }
  };

  /**
   * No-op constructor.
   *
   * This makes it easy to inject or configure using web.xml.
   */
  public ServiceManagementConfigFilter() {
    super(LOADER);
  }
}
