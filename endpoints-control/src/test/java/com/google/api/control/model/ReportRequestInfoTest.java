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

import static org.junit.Assert.fail;

import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.client.util.Strings;
import com.google.api.servicecontrol.v1.Distribution;
import com.google.api.servicecontrol.v1.LogEntry;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.common.truth.Truth;
import com.google.logging.type.LogSeverity;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ReportingRequestInfoTest tests the behavior in {@code ReportRequestInfo}.
 */
@RunWith(JUnit4.class)
public class ReportRequestInfoTest {
  private static final String NO_LOGS = "";
  private static final String TEST_OPERATION_NAME = "anOperationName";
  private static final String TEST_OPERATION_ID = "anOperationId";
  private static final String TEST_SERVICE_NAME = "aServiceName";
  private static final String TEST_API_KEY = "test_api_key";
  private static FakeClock TEST_CLOCK = new FakeClock();
  static {
    TEST_CLOCK.tick(2L, TimeUnit.SECONDS);
  }
  private static Timestamp REALLY_EARLY = Timestamps.now(TEST_CLOCK);
  private static final long TEST_LATENCY = 7L;
  private static final long TEST_SIZE = 11L;
  private static final URL BASE_LOGGING_SERVICE_JSON =
      ReportingRuleTest.class.getClassLoader().getResource("report_request_info_base_logging.json");
  private static final URL BASE_METRICS_SERVICE_JSON =
      ReportingRuleTest.class.getClassLoader().getResource("report_request_info_base_metrics.json");

  @Test
  public void shouldFailAsReportRequestWhenIncomplete() {
    try {
      new ReportRequestInfo().asReportRequest(noopReportingRules(), TEST_CLOCK);
      fail("Should fail with IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void asReportRequestShouldAddExpectedLogs() throws IOException {
    Service logsOnly = fromUrl(BASE_LOGGING_SERVICE_JSON);
    ReportingRule rules = ReportingRule.fromService(logsOnly);
    for (InfoTest t : logTests(logsOnly.getLogs(0).getName())) {
      Truth.assertThat(t.givenInfo.asReportRequest(rules, TEST_CLOCK).getOperations(0)).isEqualTo(
          t.wantedOp);
    }
  }

  @Test
  public void asReportRequestShouldAddExpectedMetrics() throws IOException {
    Service metricsOnly = fromUrl(BASE_METRICS_SERVICE_JSON);
    ReportingRule rules = ReportingRule.fromService(metricsOnly);
    for (InfoTest t : metricTests(NO_LOGS)) {
      Truth.assertThat(t.givenInfo.asReportRequest(rules, TEST_CLOCK).getOperations(0)).isEqualTo(
          t.wantedOp);
    }
  }

  private static final InfoTest[] logTests(String name) throws IOException {
    return new InfoTest[] {
        new InfoTest(newBaseInfo(), newBaseOperation(name, 0)),
        new InfoTest(newBaseInfo().setResponseCode(404), newBaseOperation(name, 404))};
  }

  private static final InfoTest[] metricTests(String name) throws IOException {
    Distribution sample = Distributions.addSample(TEST_SIZE, newTestExponentialDistribution());
    MetricValue sampleValue = MetricValue.newBuilder().setDistributionValue(sample).build();
    MetricValueSet.Builder metricValues =
        MetricValueSet.newBuilder().addMetricValues(sampleValue).setMetricName(
            KnownMetrics.CONSUMER_REQUEST_SIZES.getName());
    return new InfoTest[] {
        new InfoTest(newBaseInfo(), newBaseOperation(name, 0)
            .addMetricValueSets(metricValues))};
  }

  private static final Distribution newTestExponentialDistribution() {
    return Distributions.createExponential(8, 10, 1.0);
  }

  private static final Service fromUrl(URL u) throws IOException {
    String jsonText = Resources.toString(u, Charset.defaultCharset());
    Builder b = Service.newBuilder();
    JsonFormat.parser().merge(jsonText, b);
    return b.build();
  }

  private static final class InfoTest {
    private Operation wantedOp;
    private ReportRequestInfo givenInfo;

    InfoTest(ReportRequestInfo info, Operation.Builder b) {
      wantedOp = b.build();
      givenInfo = info;
    }
  }

  private static Operation.Builder newBaseOperation(String logName, int responseCode) {
    Operation.Builder res = Operation
        .newBuilder()
        .setConsumerId("api_key:" + TEST_API_KEY)
        .setImportance(Importance.LOW)
        .setOperationId(TEST_OPERATION_ID)
        .setOperationName(TEST_OPERATION_NAME)
        .setEndTime(REALLY_EARLY)
        .setStartTime(REALLY_EARLY);
    if (!Strings.isNullOrEmpty(logName)) {
      res.addLogEntries(newTestLogEntry(logName, responseCode));
    }
    return res;
  }

  private static ReportRequestInfo newBaseInfo() {
    return (ReportRequestInfo) new ReportRequestInfo(newTestOperationInfo())
        .setMethod("GET")
        .setRequestTimeMillis(TEST_LATENCY)
        .setOverheadTimeMillis(TEST_LATENCY)
        .setBackendTimeMillis(TEST_LATENCY)
        .setRequestSize(TEST_SIZE)
        .setResponseSize(TEST_SIZE)
        .setApiKey(TEST_API_KEY)
        .setApiKeyValid(true);
  }

  private static LogEntry.Builder newTestLogEntry(String name, int responseCode) {
    Value.Builder vb = Value.newBuilder();
    Map<String, Value> values = Maps.newHashMap();
    values.put("api_key", vb.setStringValue(TEST_API_KEY).build());
    values.put("http_method", vb.setStringValue("GET").build());
    values.put("timestamp",
        vb.setNumberValue(TEST_CLOCK.currentTimeMillis()).build());
    values.put("http_response_code", vb.setNumberValue(responseCode).build());
    values.put("response_size", vb.setNumberValue(TEST_SIZE).build());
    values.put("request_size", vb.setNumberValue(TEST_SIZE).build());
    if (responseCode >= 400) {
      values.put("error_cause", vb.setStringValue("internal").build());
    }
    return LogEntry
        .newBuilder()
        .setStructPayload(Struct.newBuilder().putAllFields(values))
        .setName(name)
        .setSeverity(responseCode >= 400 ? LogSeverity.ERROR : LogSeverity.INFO)
        .setTimestamp(REALLY_EARLY);
  }

  private static OperationInfo newTestOperationInfo() {
    return new OperationInfo()
        .setOperationId(TEST_OPERATION_ID)
        .setOperationName(TEST_OPERATION_NAME)
        .setServiceName(TEST_SERVICE_NAME);
  }

  private static ReportingRule noopReportingRules() {
    return ReportingRule.fromService(Service.newBuilder().build());
  }
}
