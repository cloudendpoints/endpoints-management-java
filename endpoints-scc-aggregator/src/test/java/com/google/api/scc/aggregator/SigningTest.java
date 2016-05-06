/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.scc.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
