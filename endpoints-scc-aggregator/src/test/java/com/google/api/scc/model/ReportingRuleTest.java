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

package com.google.api.scc.model;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.protobuf.util.JsonFormat;

/**
 * ReportingRuleTest tests the behavior in {@code ReportingRule}.
 */
@RunWith(JUnit4.class)
public class ReportingRuleTest {
  private static final List<KnownLabels> WANTED_LABELS =
      ImmutableList.of(KnownLabels.REFERER, KnownLabels.RESPONSE_CODE_CLASS, KnownLabels.PROTOCOL);
  private static final Set<String> LABEL_NAMES =
      ImmutableSet.of(KnownLabels.REFERER.getName(), KnownLabels.RESPONSE_CODE_CLASS.getName(),
          KnownLabels.PROTOCOL.getName());

  private static final List<KnownMetrics> WANTED_METRICS = ImmutableList
      .of(KnownMetrics.CONSUMER_REQUEST_SIZES, KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT);
  private static final Set<String> METRIC_NAMES =
      ImmutableSet.of(KnownMetrics.CONSUMER_REQUEST_SIZES.getName(),
          KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT.getName());

  private static final List<String> WANTED_LOGS =
      ImmutableList.of("my-endpoints-log", "my-alt-endpoints-log");
  private static final URL MONITORED_SERVICE_JSON =
      ReportingRuleTest.class
          .getClassLoader()
          .getResource("reporting_rule_create_from_a_service.json");

  @Test
  public void shouldCreateFromKnownInputsOk() {
    ReportingRule rule = ReportingRule.fromKnownInputs(WANTED_LOGS.toArray(new String[] {}),
        METRIC_NAMES, LABEL_NAMES);
    assertThat(Arrays.asList(rule.getLabels())).containsExactlyElementsIn(WANTED_LABELS);
    assertThat(Arrays.asList(rule.getMetrics())).containsExactlyElementsIn(WANTED_METRICS);
    assertThat(Arrays.asList(rule.getLogs())).containsExactlyElementsIn(WANTED_LOGS);
  }

  @Test
  public void shouldCreateFromAServiceOk() throws IOException {
    String jsonText = Resources.toString(MONITORED_SERVICE_JSON, Charset.defaultCharset());
    Builder b = Service.newBuilder();
    JsonFormat.parser().merge(jsonText, b);
    ReportingRule rule =
        ReportingRule.fromService(b.build());
    assertThat(Arrays.asList(rule.getLabels())).containsExactlyElementsIn(WANTED_LABELS);
    assertThat(Arrays.asList(rule.getMetrics())).containsExactlyElementsIn(WANTED_METRICS);
    assertThat(Arrays.asList(rule.getLogs())).containsExactlyElementsIn(WANTED_LOGS);
  }
}
