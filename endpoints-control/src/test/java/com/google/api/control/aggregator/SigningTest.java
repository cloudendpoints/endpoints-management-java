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

package com.google.api.control.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.control.aggregator.Signing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 * Tests for Signing.
 */
@RunWith(JUnit4.class)
public class SigningTest {
  private static final Map<String, String> TEST_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  @Test
  public void putLabelsShouldUpdateHashesConsistently() {
    HashMap<String, String> copy1 = Maps.newHashMap(TEST_LABELS);
    HashMap<String, String> copy2 = Maps.newHashMap(TEST_LABELS);
    HashFunction hf = Hashing.md5();
    Hasher hasher1 = hf.newHasher();
    Hasher hasher2 = hf.newHasher();
    assertEquals(Signing.putLabels(hasher1, copy1).hash(),
        Signing.putLabels(hasher2, copy2).hash());
  }

  @Test
  public void putLabelsShouldVaryHashesWithValues() {
    HashMap<String, String> copy1 = Maps.newHashMap(TEST_LABELS);
    HashMap<String, String> copy2 = Maps.newHashMap(TEST_LABELS);
    copy2.put("key1", "changed!");
    HashFunction hf = Hashing.md5();
    Hasher hasher1 = hf.newHasher();
    Hasher hasher2 = hf.newHasher();
    assertNotEquals(Signing.putLabels(hasher1, copy1).hash(),
        Signing.putLabels(hasher2, copy2).hash());
  }
}
