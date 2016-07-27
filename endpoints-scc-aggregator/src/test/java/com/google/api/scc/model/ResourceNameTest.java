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

package com.google.api.scc.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.truth.Truth;

/**
 * Tests for {@link ResourceName}. As resource names are mostly a wrapper around path
 * templates, not much needs to be done here.
 */
@RunWith(JUnit4.class)
public class ResourceNameTest {

  @Test
  public void resourceNameMethods() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/**");
    ResourceName name = ResourceName.create(template, "buckets/b/objects/1/2");
    Truth.assertThat(name.toString()).isEqualTo("buckets/b/objects/1/2");
    Truth.assertThat(name.get("$1")).isEqualTo("1/2");
    Truth.assertThat(name.get("$0")).isEqualTo("b");
    Truth.assertThat(name.parentName().toString()).isEqualTo("buckets/b/objects");
  }
}
