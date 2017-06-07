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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.Service;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Clock;
import com.google.api.control.ControlFilter.FilterServletOutputStream;
import com.google.api.control.aggregator.FakeTicker;
import com.google.api.control.model.CheckErrorInfo;
import com.google.api.control.model.CheckRequestInfo;
import com.google.api.control.model.FakeClock;
import com.google.api.control.model.KnownLabels;
import com.google.api.control.model.KnownMetrics;
import com.google.api.control.model.MethodRegistry;
import com.google.api.control.model.MethodRegistry.QuotaInfo;
import com.google.api.control.model.OperationInfo;
import com.google.api.control.model.ReportRequestInfo.ReportedPlatforms;
import com.google.api.control.model.ReportingRule;
import com.google.api.control.model.Timestamps;
import com.google.api.servicecontrol.v1.CheckError;
import com.google.api.servicecontrol.v1.CheckError.Code;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests for {@code ControlFilter}.
 */
@RunWith(JUnit4.class)
public class ControlFilterTest {
  private static final Set<String> LABEL_NAMES = ImmutableSet.of(KnownLabels.REFERER.getName(),
      KnownLabels.RESPONSE_CODE_CLASS.getName(), KnownLabels.PROTOCOL.getName());
  private static final Set<String> METRIC_NAMES =
      ImmutableSet.of(KnownMetrics.CONSUMER_REQUEST_SIZES.getName(),
          KnownMetrics.CONSUMER_REQUEST_ERROR_COUNT.getName());
  private static final List<String> WANTED_LOGS =
      ImmutableList.of("my-endpoints-log", "my-alt-endpoints-log");

  private static final String TEST_METHOD = "GET";
  private static final String TEST_PROJECT_ID = "aProjectId";
  private static final String TEST_SELECTOR = "mockedSelector";
  private static final String TEST_URI = "my/test/uri";
  private static final Integer TEST_REQUEST_SIZE = 5;
  private static final String TEST_SERVICE_NAME = "testService";
  private static final String TEST_CONFIG_ID = "testConfigId";
  private static final String REFERER = "testReferer";
  private static final String TEST_CLIENT_IP = "196.168.0.3";
  private static final Service TEST_SERVICE = Service.newBuilder()
      .setId(TEST_CONFIG_ID)
      .setName(TEST_SERVICE_NAME)
      .build();
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private Client client;
  private Clock testClock;
  private Ticker testTicker;
  private MethodRegistry.Info info;
  private ArgumentCaptor<CheckRequest> capturedCheck;
  private ArgumentCaptor<ReportRequest> capturedReport;
  private ByteArrayOutputStream responseContent;
  private ReportingRule rule;
  private CheckResponse checkResponse;
  private static final Timestamp REALLY_EARLY = Timestamps.fromEpoch(0);
  private static final Map<String, String> OPERATION_LABELS =
      ImmutableMap.of(CheckRequestInfo.SCC_CALLER_IP, TEST_CLIENT_IP, OperationInfo.SCC_REFERER,
          REFERER, OperationInfo.SCC_USER_AGENT, "ESP", OperationInfo.SCC_SERVICE_AGENT,
          KnownLabels.SERVICE_AGENT);

  @Before
  public void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    responseContent = new ByteArrayOutputStream();
    chain = mock(FilterChain.class);
    client = mock(Client.class);
    testClock = new FakeClock();
    testTicker = new FakeTicker(true);
    info = new MethodRegistry.Info(TEST_SELECTOR, null, QuotaInfo.DEFAULT);
    info.setAllowUnregisteredCalls(true);
    capturedCheck = ArgumentCaptor.forClass(CheckRequest.class);
    capturedReport = ArgumentCaptor.forClass(ReportRequest.class);

    // This rule limits determines what metrics and labels are reported, simplifying testing
    //
    // In each test, rule can be modified before calling mockRequestAndResponse to control
    // the expected metrics/labels that appear in the report request
    rule = ReportingRule.fromKnownInputs(WANTED_LOGS.toArray(new String[] {}), METRIC_NAMES,
        LABEL_NAMES);
    checkResponse = CheckResponse.newBuilder().build();
  }

  @Test
  public void shouldCallTheChainIfThereIsNoClient() throws IOException, ServletException {
    ControlFilter f = new ControlFilter(null, "aProjectId", testTicker, testClock, null);
    f.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldNotUseTheClientWithoutAProjectId() throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, null, testTicker, testClock, null);
    f.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    verify(client, never()).check(capturedCheck.capture());
  }

  @Test
  public void shouldNotUseTheClientIfThereIsNoMethodInfo() throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    when(request.getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE)).thenReturn(null);
    f.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    verify(client, never()).check(capturedCheck.capture());
  }

  @Test
  public void shouldUseTheDefaultLocation() throws IOException, ServletException {
    rule = ReportingRule.fromKnownInputs(null, null,
        Sets.newHashSet(KnownLabels.GCP_LOCATION.getName()));
    mockRequestAndResponse();
    when(client.check(any(CheckRequest.class))).thenReturn(checkResponse);

    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    f.doFilter(request, response, chain);
    verify(client, times(1)).report(capturedReport.capture());
    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.of(KnownLabels.GCP_LOCATION.getName(), "global", OperationInfo.SCC_USER_AGENT,
            "ESP", OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT,
            KnownLabels.SCC_PLATFORM.getName(), "Unknown");
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
    // TODO: Add more assertions
  }

  @Test
  public void shouldSetBackendLatency() throws IOException, ServletException {
    HashSet<String> wantMetricNames =
        Sets.newHashSet(KnownMetrics.PRODUCER_BACKEND_LATENCIES.getName());
    rule = ReportingRule.fromKnownInputs(null, wantMetricNames, null);
    mockRequestAndResponse();
    when(client.check(any(CheckRequest.class))).thenReturn(checkResponse);

    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    f.doFilter(request, response, chain);
    verify(client, times(1)).report(capturedReport.capture());
    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    Operation op = aReport.getOperations(0);

    // verify that the report includes the specified metrics
    List<MetricValueSet> mvs = op.getMetricValueSetsList();
    assertThat(mvs.size()).isEqualTo(1);
    Set<String> gotMetricNames = Sets.newHashSet();
    for (MetricValueSet s : mvs) {
      gotMetricNames.add(s.getMetricName());
    }
    assertThat(gotMetricNames).isEqualTo(wantMetricNames);
  }

  @Test
  public void shouldUseTheClientIfConfiguredOk() throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    mockRequestAndResponse();
    when(client.check(any(CheckRequest.class))).thenReturn(checkResponse);

    f.doFilter(request, response, chain);
    verify(request, times(1)).getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE);
    verify(client, times(1)).check(capturedCheck.capture());
    verify(client, times(1)).report(capturedReport.capture());
    CheckRequest aCheck = capturedCheck.getValue();
    assertThatCheckHasExpectedValues(aCheck);

    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.<String, String>builder()
            .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "2xx")
            .put(KnownLabels.PROTOCOL.getName(), "HTTP")
            .put(KnownLabels.REFERER.getName(), "testReferer")
            .put(OperationInfo.SCC_USER_AGENT, "ESP")
            .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
            .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
            .build();
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
    // TODO: Add more assertions
  }

  @Test
  public void shouldSendTheDefaultApiKeyIfPresent() throws IOException, ServletException {
    String[] defaultKeyNames = {"key", "api_key"};
    String testApiKey = "defaultApiKey";
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    for (String defaultKeyName : defaultKeyNames) {
      mockRequestAndResponse();
      when(request.getParameter(defaultKeyName)).thenReturn(testApiKey);
      when(client.check(any(CheckRequest.class))).thenReturn(checkResponse);

      f.doFilter(request, response, chain);
      verify(client, times(1)).check(capturedCheck.capture());
      verify(client, times(1)).report(capturedReport.capture());
      CheckRequest aCheck = capturedCheck.getValue();
      assertThat(aCheck.getOperation().getOperationId()).isNotNull();
      CheckRequest.Builder comparedCheck = aCheck.toBuilder();
      comparedCheck.getOperationBuilder().clearOperationId();
      CheckRequest.Builder wantedCheck = wantedCheckRequest();

      // Confirm that the consumer name includes the api key
      wantedCheck.getOperationBuilder().setConsumerId("api_key:" + testApiKey);
      assertThat(comparedCheck.build()).isEqualTo(wantedCheck.build());

      ReportRequest aReport = capturedReport.getValue();
      assertThat(aReport.getOperationsCount()).isEqualTo(1);
      assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
      Operation op = aReport.getOperations(0);
      Map<String, String> wantedLabels =
          ImmutableMap.<String, String>builder()
              .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "2xx")
              .put(KnownLabels.PROTOCOL.getName(), "HTTP")
              .put(KnownLabels.REFERER.getName(), "testReferer")
              .put(OperationInfo.SCC_USER_AGENT, "ESP")
              .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
              .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
              .build();
      assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
      assertThat(op.getConsumerId()).isEqualTo("api_key:" + testApiKey);
      reset(client);
      reset(request);
    }
  }

  @Test
  public void shouldSendAReportButNotInvokeTheChainIfTheCheckFails()
      throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);

    // Fail because the project got deleted
    CheckResponse deleted = CheckResponse
        .newBuilder()
        .addCheckErrors(CheckError.newBuilder().setCode(Code.PROJECT_DELETED))
        .build();
    mockRequestAndResponse();
    when(client.check(any(CheckRequest.class))).thenReturn(deleted);

    f.doFilter(request, response, chain);
    verify(request, times(1)).getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE);
    verify(response, times(1)).sendError(HttpServletResponse.SC_FORBIDDEN,
        "Project " + TEST_PROJECT_ID + " has been deleted");
    verify(client, times(1)).check(capturedCheck.capture());
    verify(client, times(1)).report(capturedReport.capture());
    verify(chain, never()).doFilter(request, response);

    // verify the check
    CheckRequest aCheck = capturedCheck.getValue();
    assertThatCheckHasExpectedValues(aCheck);

    // verify the report
    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.<String, String>builder()
            .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "4xx")
            .put(KnownLabels.PROTOCOL.getName(), "HTTP")
            .put(KnownLabels.REFERER.getName(), "testReferer")
            .put(OperationInfo.SCC_USER_AGENT, "ESP")
            .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
            .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
            .build();
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
  }

  @Test
  public void shouldSendAReportButNotInvokeTheChainIfTheCheckFailsOnBadApiKey()
      throws IOException, ServletException {
    String testApiKey = "defaultApiKey";
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);

    // Fail because the project got deleted
    CheckResponse deleted = CheckResponse
        .newBuilder()
        .addCheckErrors(CheckError.newBuilder().setCode(Code.API_KEY_EXPIRED))
        .build();
    mockRequestAndResponse();
    when(request.getParameter("key")).thenReturn(testApiKey);
    when(client.check(any(CheckRequest.class))).thenReturn(deleted);

    f.doFilter(request, response, chain);
    verify(request, times(1)).getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE);
    verify(response, times(1)).sendError(HttpServletResponse.SC_BAD_REQUEST,
        CheckErrorInfo.API_KEY_EXPIRED.getMessage());
    verify(client, times(1)).check(capturedCheck.capture());
    verify(client, times(1)).report(capturedReport.capture());
    verify(chain, never()).doFilter(request, response);

    // Confirm that the consumer name includes the api key
    CheckRequest aCheck = capturedCheck.getValue();
    CheckRequest.Builder comparedCheck = aCheck.toBuilder();
    comparedCheck.getOperationBuilder().clearOperationId();
    CheckRequest.Builder wantedCheck = wantedCheckRequest();
    wantedCheck.getOperationBuilder().setConsumerId("api_key:" + testApiKey);
    assertThat(comparedCheck.build()).isEqualTo(wantedCheck.build());

    // verify the report
    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.<String, String>builder()
            .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "4xx")
            .put(KnownLabels.PROTOCOL.getName(), "HTTP")
            .put(KnownLabels.REFERER.getName(), "testReferer")
            .put(OperationInfo.SCC_USER_AGENT, "ESP")
            .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
            .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
            .build();
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);

    // confirm that the report uses a consumer id derived from the project
    assertThat(op.getConsumerId()).isEqualTo("project:" + TEST_PROJECT_ID);
  }


  @Test
  public void shouldSendAReportAndInvokeTheChainIfTheCheckErrors()
      throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);

    // Return null from check to indicate that transport fail occurred
    mockRequestAndResponse();
    when(client.check(any(CheckRequest.class))).thenReturn(null);

    f.doFilter(request, response, chain);
    verify(request, times(1)).getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE);
    verify(client, times(1)).check(capturedCheck.capture());
    verify(client, times(1)).report(capturedReport.capture());
    verify(chain, times(1)).doFilter(eq(request), any(HttpServletResponse.class));

    CheckRequest aCheck = capturedCheck.getValue();
    assertThatCheckHasExpectedValues(aCheck);

    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.<String, String>builder()
            .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "2xx")
            .put(KnownLabels.PROTOCOL.getName(), "HTTP")
            .put(KnownLabels.REFERER.getName(), "testReferer")
            .put(OperationInfo.SCC_USER_AGENT, "ESP")
            .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
            .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
            .build();
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
  }

  @Test
  public void shouldStopTheClientWhenDestroyed() throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    f.destroy();
    verify(client, times(1)).stop();
  }

  @Test
  public void shouldSendAReportButNotInvokeTheChainWhenNeededApiKeyIsNotProvided()
      throws IOException, ServletException {
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);

    // Fail because the api key is needed but no provided
    mockRequestAndResponse();
    info.setAllowUnregisteredCalls(false);

    f.doFilter(request, response, chain);
    verify(request, times(1)).getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE);
    verify(response, times(1)).sendError(HttpServletResponse.SC_UNAUTHORIZED,
        CheckErrorInfo.API_KEY_NOT_PROVIDED.fullMessage("", ""));
    verify(client, never()).check(any(CheckRequest.class));
    verify(client, times(1)).report(capturedReport.capture());
    verify(chain, never()).doFilter(request, response);

    // verify the report
    ReportRequest aReport = capturedReport.getValue();
    assertThat(aReport.getOperationsCount()).isEqualTo(1);
    assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
    Operation op = aReport.getOperations(0);
    Map<String, String> wantedLabels =
        ImmutableMap.<String, String>builder()
            .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "4xx")
            .put(KnownLabels.PROTOCOL.getName(), "HTTP")
            .put(KnownLabels.REFERER.getName(), "testReferer")
            .put(OperationInfo.SCC_USER_AGENT, "ESP")
            .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
            .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
            .build();
    assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
  }

  @Test
  public void shouldSucceedWhenANeededApiKeyIsPresent() throws IOException, ServletException {
    String[] defaultKeyNames = {"key", "api_key"};
    String testApiKey = "defaultApiKey";
    ControlFilter f = new ControlFilter(client, TEST_PROJECT_ID, testTicker, testClock, null);
    info.setAllowUnregisteredCalls(false); // the means that the API key is necessary

    for (String defaultKeyName : defaultKeyNames) {
      mockRequestAndResponse();
      when(request.getParameter(defaultKeyName)).thenReturn(testApiKey);
      when(client.check(any(CheckRequest.class))).thenReturn(checkResponse);

      f.doFilter(request, response, chain);
      verify(client, times(1)).check(capturedCheck.capture());
      verify(client, times(1)).report(capturedReport.capture());
      CheckRequest aCheck = capturedCheck.getValue();
      assertThat(aCheck.getOperation().getOperationId()).isNotNull();
      CheckRequest.Builder comparedCheck = aCheck.toBuilder();
      comparedCheck.getOperationBuilder().clearOperationId();
      CheckRequest.Builder wantedCheck = wantedCheckRequest();

      // Confirm that the consumer name includes the api key
      wantedCheck.getOperationBuilder().setConsumerId("api_key:" + testApiKey);
      assertThat(comparedCheck.build()).isEqualTo(wantedCheck.build());

      ReportRequest aReport = capturedReport.getValue();
      assertThat(aReport.getOperationsCount()).isEqualTo(1);
      assertThat(aReport.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
      Operation op = aReport.getOperations(0);
      Map<String, String> wantedLabels =
          ImmutableMap.<String, String>builder()
              .put(KnownLabels.RESPONSE_CODE_CLASS.getName(), "2xx")
              .put(KnownLabels.PROTOCOL.getName(), "HTTP")
              .put(KnownLabels.REFERER.getName(), "testReferer")
              .put(OperationInfo.SCC_USER_AGENT, "ESP")
              .put(OperationInfo.SCC_SERVICE_AGENT, KnownLabels.SERVICE_AGENT)
              .put(KnownLabels.SCC_PLATFORM.getName(), "Unknown")
              .build();
      assertThat(op.getLabelsMap()).isEqualTo(wantedLabels);
      assertThat(op.getConsumerId()).isEqualTo("api_key:" + testApiKey);
      reset(client);
      reset(request);
    }
  }

  @Test
  public void getPlatformFromEnvironment_Gke() {
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.of("KUBERNETES_SERVICE_HOST", "test"), new Properties(),
            new MetadataTransport(true)))
        .isEqualTo(ReportedPlatforms.GKE);
  }

  @Test
  public void getPlatformFromEnvironment_Gce() {
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.<String, String>of(), new Properties(), new MetadataTransport(true)))
        .isEqualTo(ReportedPlatforms.GCE);
  }

  @Test
  public void getPlatformFromEnvironment_GaeStandard() {
    Properties properties = new Properties();
    properties.setProperty("com.google.appengine.runtime.environment", "Production");
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.of("GAE_MODULE_NAME", "test"), properties, new MetadataTransport(false)))
        .isEqualTo(ReportedPlatforms.GAE_STANDARD);
  }

  @Test
  public void getPlatformFromEnvironment_GaeFlex() {
    Properties properties = new Properties();
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.of("GAE_SERVICE", "test"), properties, new MetadataTransport(true)))
        .isEqualTo(ReportedPlatforms.GAE_FLEX);
  }

  @Test
  public void getPlatformFromEnvironment_Development() {
    Properties properties = new Properties();
    properties.setProperty("com.google.appengine.runtime.environment", "Development");
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.of("SERVER_SOFTWARE", "Development"), properties,
            new MetadataTransport(false)))
        .isEqualTo(ReportedPlatforms.DEVELOPMENT);
  }

  @Test
  public void getPlatformFromEnvironment_Unknown() {
    assertThat(
        ControlFilter.getPlatformFromEnvironment(
            ImmutableMap.<String, String>of(), new Properties(), new MetadataTransport(false)))
        .isEqualTo(ReportedPlatforms.UNKNOWN);
  }

  private static class MetadataTransport extends HttpTransport {
    private final boolean hasMetadata;

    MetadataTransport(boolean hasMetadata) {
      this.hasMetadata = hasMetadata;
    }

    @Override
    protected LowLevelHttpRequest buildRequest(String s, String s1) throws IOException {
      return new MetadataRequest(hasMetadata);
    }
  }

  private static class MetadataRequest extends LowLevelHttpRequest {
    private final boolean hasMetadata;

    MetadataRequest(boolean hasMetadata) {
      this.hasMetadata = hasMetadata;
    }

    @Override
    public void addHeader(String s, String s1) throws IOException {

    }

    @Override
    public LowLevelHttpResponse execute() throws IOException {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
      if (hasMetadata) {
        response.addHeader("Metadata-Flavor", "Google");
        return response;
      }
      throw new IOException("emulated server exception");
    }
  }

  private void assertThatCheckHasExpectedValues(CheckRequest aCheck) {
    assertThat(aCheck.getOperation().getOperationId()).isNotNull();
    CheckRequest.Builder comparedCheck = aCheck.toBuilder();
    comparedCheck.getOperationBuilder().clearOperationId();
    assertThat(comparedCheck.build()).isEqualTo(wantedCheckRequest().build());
  }

  private CheckRequest.Builder wantedCheckRequest() {
    Operation.Builder op = Operation
        .newBuilder()
        .setConsumerId("project:" + TEST_PROJECT_ID)
        .setOperationName(TEST_SELECTOR)
        .setEndTime(REALLY_EARLY)
        .setStartTime(REALLY_EARLY)
        .putAllLabels(OPERATION_LABELS);
    return CheckRequest.newBuilder().setServiceName(TEST_SERVICE_NAME).setOperation(op);
  }

  private void mockRequestAndResponse() throws IOException {
    when(response.getOutputStream()).thenReturn(new FilterServletOutputStream(responseContent));
    when(request.getAttribute(ConfigFilter.METHOD_INFO_ATTRIBUTE)).thenReturn(info);
    when(request.getAttribute(ConfigFilter.REPORTING_ATTRIBUTE)).thenReturn(rule);
    when(request.getAttribute(ConfigFilter.SERVICE_ATTRIBUTE)).thenReturn(TEST_SERVICE);
    when(request.getParameter(anyString())).thenReturn(null);
    when(request.getHeaders(anyString())).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn(TEST_CLIENT_IP);
    when(request.getHeader("referer")).thenReturn(REFERER);
    when(request.getAttribute(ConfigFilter.SERVICE_NAME_ATTRIBUTE)).thenReturn(TEST_SERVICE_NAME);
    when(request.getMethod()).thenReturn(TEST_METHOD);
    when(request.getRequestURI()).thenReturn(TEST_URI);
    when(request.getContentLength()).thenReturn(TEST_REQUEST_SIZE);
  }
}
