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

package com.google.api.control.model;

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
import com.google.api.control.model.KnownLabels;
import com.google.api.control.model.KnownMetrics;
import com.google.api.control.model.ReportingRule;
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
