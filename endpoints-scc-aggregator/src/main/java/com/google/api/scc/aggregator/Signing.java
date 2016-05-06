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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.hash.Hasher;

/**
 * Provides functions that support the creation of signatures.
 */
public final class Signing {
  private Signing() {}

  /**
   * Updates {@code h} with the contents of {@code labels}.
   *
   * {@code labels} can be any Map<String, String>, but will typically be labels, but will typically
   * be the labels of one of the model protobufs.
   *
   * @param h a {@link Hasher}
   * @param labels some labels
   * @return the {@code Hasher}, to allow fluent-style usage
   */
  public static Hasher putLabels(Hasher h, Map<String, String> labels) {
    for (Map.Entry<String, String> labelsEntry : labels.entrySet()) {
      h.putChar('\0');
      h.putString(labelsEntry.getKey(), StandardCharsets.UTF_8);
      h.putChar('\0');
      h.putString(labelsEntry.getValue(), StandardCharsets.UTF_8);
    }
    return h;
  }
}
