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
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.Clock;
import com.google.api.control.model.CheckErrorInfo;
import com.google.api.control.model.CheckRequestInfo;
import com.google.api.control.model.MethodRegistry;
import com.google.api.control.model.OperationInfo;
import com.google.api.control.model.QuotaErrorInfo;
import com.google.api.control.model.QuotaRequestInfo;
import com.google.api.control.model.ReportRequestInfo;
import com.google.api.control.model.ReportRequestInfo.ReportedPlatforms;
import com.google.api.control.model.ReportRequestInfo.ReportedProtocols;
import com.google.api.control.model.ReportingRule;
import com.google.api.servicecontrol.v1.AllocateQuotaRequest;
import com.google.api.servicecontrol.v1.AllocateQuotaResponse;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * ControlFilter is a {@link Filter} that provides service control.
 *
 * Requests do not proceed unless the response to a call
 * {@link Client#check(com.google.api.servicecontrol.v1.CheckRequest)} succeeds. All calls are
 * 'reported' via a call to {@link Client#report(com.google.api.servicecontrol.v1.ReportRequest)}
 */
public class ControlFilter implements Filter {
  private static final Logger log = Logger.getLogger(ControlFilter.class.getName());
  private static final String REFERER = "referer";
  private static final String PROJECT_ID_PARAM = "endpoints.projectId";
  private static final String SERVICE_NAME_PARAM = "endpoints.serviceName";
  private static final String STATS_LOG_FREQUENCY_PARAM = "endpoints.statsLogFrequency";
  private static final String DEFAULT_LOCATION = "global";
  private static final List<String> DEFAULT_API_KEYS = ImmutableList.of("key", "api_key");
  private static final String METADATA_SERVER_URL = "http://metadata.google.internal";
  private static final String API_PROXY_EXCEPTION_CLASS_NAME =
      "com.google.apphosting.api.ApiProxy.ApiProxyException";
  private static final String REMOTE_API_EXCEPTION_CLASS_NAME =
      "com.google.appengine.tools.remoteapi.RemoteApiException";
  private final Ticker ticker;
  private final Clock clock;
  private final ReportedPlatforms platform;
  private String projectId;
  private Client client;
  private int statsLogFrequency = Client.DO_NOT_LOG_STATS;
  private Statistics statistics;

  @VisibleForTesting
  public ControlFilter(@Nullable Client client, @Nullable String projectId, @Nullable Ticker ticker,
      @Nullable Clock clock, @Nullable HttpTransport transport) {
    this.client = client;
    setProjectId(projectId);
    this.ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.clock = clock == null ? Clock.SYSTEM : clock;
    transport = transport == null ? new NetHttpTransport() : transport;
    platform = getPlatformFromEnvironment(System.getenv(), System.getProperties(), transport);
    this.statistics = new Statistics();
  }

  public ControlFilter() {
    this(null, null, null, null, null);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String configProjectId = filterConfig.getInitParameter(PROJECT_ID_PARAM);
    if (!Strings.isNullOrEmpty(configProjectId)) {
      setProjectId(configProjectId);
    }
    String statsFrequencyText = filterConfig.getInitParameter(STATS_LOG_FREQUENCY_PARAM);
    try {
      if (!Strings.isNullOrEmpty(statsFrequencyText)) {
        statsLogFrequency = Integer.parseInt(statsFrequencyText);
        log.log(Level.WARNING, String.format("will log stats every %d reports", statsLogFrequency));
      }
    } catch (NumberFormatException e) {
      log.log(Level.WARNING, String.format("ignored invalid debug stat value %s", statsFrequencyText));
    }

    String configServiceName = filterConfig.getInitParameter(SERVICE_NAME_PARAM);
    if (!Strings.isNullOrEmpty(configServiceName)) {
      try {
        this.client = createClient(configServiceName);
        this.client.start();
      } catch (GeneralSecurityException e) {
        log.log(Level.SEVERE, "could not create the control client", e);
      } catch (IOException e) {
        log.log(Level.SEVERE, "could not create the control client", e);
      }
    }
  }

  @Override
  public void destroy() {
    if (this.client != null) {
      this.client.stop();
    }
  }

  /**
   * A template method used while initializing the filter.
   *
   * <strong>Intended Usage</strong>
   * <p>
   * Subclasses of may override this method to customize how the project Id is determined
   * </p>
   */
  protected void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  protected int statsLogFrequency() {
    return statsLogFrequency;
  }

  /**
   * A template method for constructing clients.
   *
   * <strong>Intended Usage</strong>
   * <p>
   * Subclasses of may override this method to customize how the client get's built. Note that this
   * method is intended to be invoked by {@link ControlFilter#init(FilterConfig)} when the
   * serviceName parameter is provided. If that parameter is missing, this function will not be
   * called and the client will not be constructed.
   * </p>
   *
   * @param configServiceName the service name to use in constructing the client
   * @return a {@link Client}
   * @throws GeneralSecurityException indicates that client was not created successfully
   * @throws IOException indicates that the client was not created successfully
   */
  protected Client createClient(String configServiceName)
      throws GeneralSecurityException, IOException {
    return new Client.Builder(configServiceName)
        .setStatsLogFrequency(statsLogFrequency())
        .setHttpTransport(new NetHttpTransport())
        .build();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (client == null) {
      log.log(Level.INFO,
          String.format("No control client was created - skipping service control"));
      chain.doFilter(request, response);
      return;
    }
    if (projectId == null) {
      log.log(Level.INFO, String.format("No project Id was specified - skipping service control"));
      chain.doFilter(request, response);
      return;
    }

    // Start tracking the latency
    LatencyTimer timer = new LatencyTimer(ticker);

    // Service Control is not required for this method, execute the rest of
    // the filter chain
    MethodRegistry.Info info = ConfigFilter.getMethodInfo(request);
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    if (info == null) {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, String.format("no method corresponds to %s - skipping service control",
            httpRequest.getRequestURI()));
      }
      chain.doFilter(request, response);
      return;
    }

    // Internal stats tracking
    Stopwatch creationTimer = Stopwatch.createUnstarted(ticker);
    Stopwatch overallTimer = Stopwatch.createStarted(ticker);

    // Perform the check
    AppStruct appInfo = new AppStruct();
    appInfo.httpMethod = httpRequest.getMethod();
    appInfo.requestSize = httpRequest.getContentLength();
    appInfo.url = httpRequest.getRequestURI();
    CheckRequestInfo checkInfo = createCheckInfo(httpRequest, appInfo.url, info);
    CheckErrorInfo errorInfo;
    CheckResponse checkResponse = null;
    long consumerProjectNumber = 0;
    if (Strings.isNullOrEmpty(checkInfo.getApiKey()) && !info.shouldAllowUnregisteredCalls()) {
      errorInfo = CheckErrorInfo.API_KEY_NOT_PROVIDED;
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, "no api key was provided");
      }
    } else {
      creationTimer.reset().start();
      CheckRequest checkRequest = checkInfo.asCheckRequest(clock);
      statistics.totalChecks.incrementAndGet();
      statistics.totalCheckCreationTime.addAndGet(creationTimer.elapsed(TimeUnit.MILLISECONDS));
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, String.format("checking using %s", checkRequest));
      }
      checkResponse = client.check(checkRequest);
      errorInfo = CheckErrorInfo.convert(checkResponse);
      if (checkResponse != null) {
        consumerProjectNumber = checkResponse.getCheckInfo().getConsumerInfo().getProjectNumber();
      }
    }

    // Handle check failures. This includes check transport failures, in
    // which case the checkResponse is null.
    if (errorInfo != CheckErrorInfo.OK) {
      log.log(Level.WARNING,
          String.format("the check did not succeed; the response %s", checkResponse));

      // ensure the report request is created with updated api_key validity, as this determines
      // the consumer id
      checkInfo.setApiKeyValid(!errorInfo.isApiKeyError());
      appInfo.responseCode = errorInfo.getHttpCode();

      // 'Send' a report, end the latency timer to collect correct overhead and backend latencies
      timer.end();
      // trickle refers to whether or not a request should continue down the processing pipeline.
      // Previously, there was only check and report, so we could just call the filter chain if
      // check failed. Now, we need to check for quota, even if the check request fails open. The
      // more ideal solution would be to use an interceptor chain, but we'll leave it at this for
      // now.
      boolean trickle = false;
      if (errorInfo == CheckErrorInfo.API_KEY_NOT_PROVIDED) {
        // a needed API key was not provided
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.sendError(errorInfo.getHttpCode(), errorInfo.getMessage());
      } else if (checkResponse == null) {
        // the check did not complete: 'fail open'
        trickle = true;
      } else {
        // the checked failed: assume that any error information will be in the first check error
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.sendError(errorInfo.getHttpCode(),
            errorInfo.fullMessage(projectId, checkResponse.getCheckErrors(0).getDetail()));
      }
      if (!trickle) {
        ReportRequest reportRequest =
            createReportRequest(info, checkInfo, appInfo, ConfigFilter.getReportRule(request),
                timer, consumerProjectNumber);
        if (log.isLoggable(Level.FINEST)) {
          log.log(Level.FINEST, String.format("sending an error report request %s", reportRequest));
        }
        client.report(reportRequest);
        statistics.totalFiltered.incrementAndGet();
        statistics.totalFilteredTime.addAndGet(overallTimer.elapsed(TimeUnit.MILLISECONDS));
        logStatistics();
        return;
      }
    }

    QuotaRequestInfo quotaInfo = createQuotaInfo(httpRequest, info);
    if (quotaInfo.getMetricCosts().isEmpty()) {
      log.log(Level.FINE, "no metric costs for this method");
    } else {
      AllocateQuotaRequest quotaRequest = quotaInfo.asQuotaRequest(clock);
      AllocateQuotaResponse quotaResponse = client.allocateQuota(quotaRequest);
      QuotaErrorInfo quotaErrorInfo = QuotaErrorInfo.convert(quotaResponse);
      if (quotaErrorInfo.isReallyError()) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.sendError(quotaErrorInfo.getHttpCode(), quotaErrorInfo.getMessage());
        return;
      }
    }

    // Execute the request in wrapper, capture the response, then write it to the output
    GenericResponseWrapper wrapper = new GenericResponseWrapper((HttpServletResponse) response);
    try {
      timer.appStart();
      chain.doFilter(request, wrapper);
    } finally {
      timer.end();
      ServletOutputStream out = response.getOutputStream();
      out.write(wrapper.getData());
      out.close();
    }

    // Send a report
    appInfo.responseCode = wrapper.getResponseCode();
    appInfo.responseSize =
        wrapper.getContentLength() != 0 ? wrapper.getContentLength() : wrapper.getData().length;
    creationTimer.reset().start();
    ReportRequest reportRequest =
        createReportRequest(info, checkInfo, appInfo, ConfigFilter.getReportRule(request), timer,
            consumerProjectNumber);
    statistics.totalReports.incrementAndGet();
    statistics.totalReportCreationTime.addAndGet(creationTimer.elapsed(TimeUnit.MILLISECONDS));
    if (log.isLoggable(Level.FINEST)) {
      log.log(Level.FINEST, String.format("sending a report request %s", reportRequest));
    }
    client.report(reportRequest);
    statistics.totalFiltered.incrementAndGet();
    statistics.totalFilteredTime.addAndGet(overallTimer.elapsed(TimeUnit.MILLISECONDS));
    logStatistics();
  }

  private ReportRequest createReportRequest(MethodRegistry.Info info, CheckRequestInfo checkInfo,
      AppStruct appInfo, ReportingRule rules, LatencyTimer timer, long consumerProjectNumber) {
    // TODO: confirm how to fill in platform and location
    return new ReportRequestInfo(checkInfo)
        .setApiMethod(info.getSelector())
        .setLocation(DEFAULT_LOCATION)
        .setMethod(appInfo.httpMethod)
        .setOverheadTimeMillis(timer.getOverheadTimeMillis())
        .setPlatform(platform)
        .setProducerProjectId(projectId)
        .setProtocol(ReportedProtocols.HTTP)
        .setRequestSize(appInfo.requestSize)
        .setRequestTimeMillis(timer.getRequestTimeMillis())
        .setResponseCode(appInfo.responseCode)
        .setResponseSize(appInfo.responseSize)
        .setUrl(appInfo.url)
        .setBackendTimeMillis(timer.getBackendTimeMillis())
        .setConsumerProjectNumber(consumerProjectNumber)
        .asReportRequest(rules, clock);
  }

  private CheckRequestInfo createCheckInfo(HttpServletRequest request, String uri,
      MethodRegistry.Info info) {
    String serviceName = ConfigFilter.getServiceName(request);
    String apiKey = findApiKeyParam(request, info);
    if (Strings.isNullOrEmpty(apiKey)) {
      apiKey = findApiKeyHeader(request, info);
    }
    if (Strings.isNullOrEmpty(apiKey)) {
      apiKey = findDefaultApiKeyParam(request);
    }

    return new CheckRequestInfo(new OperationInfo()
        .setApiKey(apiKey)
        .setApiKeyValid(!Strings.isNullOrEmpty(apiKey))
        .setReferer(request.getHeader(REFERER))
        .setConsumerProjectId(this.projectId)
        .setOperationId(nextOperationId())
        .setOperationName(info.getSelector())
        .setServiceName(serviceName)).setClientIp(request.getRemoteAddr());
  }

  private QuotaRequestInfo createQuotaInfo(HttpServletRequest request, MethodRegistry.Info info) {
    String serviceName = ConfigFilter.getServiceName(request);
    String apiKey = findApiKeyParam(request, info);
    if (Strings.isNullOrEmpty(apiKey)) {
      apiKey = findApiKeyHeader(request, info);
    }
    if (Strings.isNullOrEmpty(apiKey)) {
      apiKey = findDefaultApiKeyParam(request);
    }
    Service service = ConfigFilter.getService(request);

    return new QuotaRequestInfo(new OperationInfo()
        .setApiKey(apiKey)
        .setApiKeyValid(!Strings.isNullOrEmpty(apiKey))
        .setReferer(request.getHeader(REFERER))
        .setConsumerProjectId(this.projectId)
        .setOperationId(nextOperationId())
        .setOperationName(info.getSelector())
        .setServiceName(serviceName))
        .setMetricCosts(info.getQuotaInfo().getMetricCosts())
        .setConfigId(service.getId());
  }

  private static String nextOperationId() {
    return UUID.randomUUID().toString();
  }

  private String findApiKeyParam(HttpServletRequest request, MethodRegistry.Info info) {
    List<String> params = info.apiKeyUrlQueryParam();
    if (params.isEmpty()) {
      return "";
    }

    for (String s : params) {
      String value = request.getParameter(s);
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private String findDefaultApiKeyParam(HttpServletRequest request) {
    for (String s : DEFAULT_API_KEYS) {
      String value = request.getParameter(s);
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private String findApiKeyHeader(HttpServletRequest request, MethodRegistry.Info info) {
    List<String> params = info.apiKeyHeaderParam();
    if (params.isEmpty()) {
      return "";
    }
    for (String s : params) {
      String value = request.getHeader(s);
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private void logStatistics() {
    if (statsLogFrequency < 1) {
      return;
    }
    if (statistics.totalFiltered.get() % statsLogFrequency == 0) {
      log.info(statistics.toString());
    }
  }

  @VisibleForTesting
  static ReportedPlatforms getPlatformFromEnvironment(
      Map<String, String> env, Properties properties, HttpTransport transport) {
    if (env.containsKey("KUBERNETES_SERVICE_HOST")) {
      return ReportedPlatforms.GKE;
    }
    boolean hasMetadataServer = hasMetadataServer(transport);
    String gaeEnvironment = properties.getProperty("com.google.appengine.runtime.environment");
    boolean onGae = gaeEnvironment != null && gaeEnvironment.startsWith("Production");
    if (hasMetadataServer && env.containsKey("GAE_SERVICE")) {
      return ReportedPlatforms.GAE_FLEX;
    } else if (hasMetadataServer) {
      return ReportedPlatforms.GCE;
    } else if (onGae) {
      return ReportedPlatforms.GAE_STANDARD;
    }
    if (gaeEnvironment != null && gaeEnvironment.startsWith("Development")) {
      return ReportedPlatforms.DEVELOPMENT;
    }
    return ReportedPlatforms.UNKNOWN;
  }

  private static boolean hasMetadataServer(HttpTransport transport) {
    try {
      HttpRequest request = transport.createRequestFactory()
          .buildGetRequest(new GenericUrl(METADATA_SERVER_URL));
      HttpResponse response = request.execute();
      HttpHeaders headers = response.getHeaders();
      return "Google".equals(headers.getFirstHeaderStringValue("Metadata-Flavor"));
    } catch (IOException | RuntimeException expected) {
      // If an error happens, it's probably safe to say the metadata service isn't available where
      // the code is running. We have to catch ApiProxyException due to the new dev server returning
      // a different error for unresolvable hostnames. Due to not wanting to put a required
      // dependency on the App Engine SDK here, we catch the generic RuntimeException and do a
      // class name check.
      if (expected instanceof RuntimeException
          && !API_PROXY_EXCEPTION_CLASS_NAME.equals(expected.getClass().getName())
          && !REMOTE_API_EXCEPTION_CLASS_NAME.equals(expected.getClass().getName())) {
        throw (RuntimeException) expected;
      }
    }
    return false;
  }

  private static class AppStruct {
    String httpMethod;
    long requestSize;
    long responseSize;
    int responseCode;
    String url;

    AppStruct() {
      responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
  }

  private static class Statistics {
    AtomicLong totalChecks = new AtomicLong();
    AtomicLong totalReports = new AtomicLong();
    AtomicLong totalCheckCreationTime = new AtomicLong();
    AtomicLong totalReportCreationTime = new AtomicLong();
    AtomicLong totalFilteredTime = new AtomicLong();
    AtomicLong totalFiltered = new AtomicLong();

    @Override
    public String toString() {
      final String nl = "\n  "; // Use a consistent space to make the output valid YAML
      return "filter_statistics:"
          + nl + "totalFiltered:" + totalFiltered.get()
          + nl + "totalFilteredTime:" + totalFilteredTime.get()
          + nl + "meanFilteredTime:" + divide(totalFilteredTime, totalFiltered)
          + nl + "totalChecks:" + totalChecks.get()
          + nl + "totalCheckCreationTime:" + totalCheckCreationTime.get()
          + nl + "meanCheckCreationTime:" + divide(totalCheckCreationTime, totalChecks)
          + nl + "totalReports:" + totalReports
          + nl + "totalReportCreationTime:" + totalReportCreationTime.get()
          + nl + "meanReportCreationTime:" + divide(totalReportCreationTime, totalReports);
    }

    private static double divide(AtomicLong dividend, AtomicLong divisor) {
      if (divisor.get() == 0) {
        return 0;
      }
      return 1.0 * dividend.get() / divisor.get();
    }
  }

  private static class LatencyTimer {
    private static final long NOT_STARTED = -1L;
    private final Stopwatch stopwatch;
    private long overheadTimeMillis = NOT_STARTED;
    private long backendTimeMillis;

    private LatencyTimer(Ticker ticker) {
      stopwatch = Stopwatch.createStarted(ticker);
    }

    void appStart() {
      overheadTimeMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
      stopwatch.reset().start();
    }

    void end() {
      backendTimeMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
      if (overheadTimeMillis == NOT_STARTED) {
        // end without appStart means that all time is overhead.
        overheadTimeMillis = backendTimeMillis;
        backendTimeMillis = 0;
      }
    }

    long getRequestTimeMillis() {
      if (overheadTimeMillis == 0 && backendTimeMillis == 0) {
        return NOT_STARTED;
      }
      return overheadTimeMillis + backendTimeMillis;
    }

    long getOverheadTimeMillis() {
      return overheadTimeMillis > 0 ? overheadTimeMillis : NOT_STARTED;
    }

    long getBackendTimeMillis() {
      return backendTimeMillis > 0 ? backendTimeMillis : NOT_STARTED;
    }
  }

  /**
   * FilterServletOutputStream is a ServletOutputStream that allows us to accurately count the
   * response size.
   */
  static class FilterServletOutputStream extends ServletOutputStream {
    private DataOutputStream stream;

    public FilterServletOutputStream(OutputStream output) {
      stream = new DataOutputStream(output);
    }

    @Override
    public void write(int b) throws IOException {
      stream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      stream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      stream.write(b, off, len);
    }
  }

  /**
   * GenericResponseWrapper extends HttpServletResponseWrapper to allow determination of the
   * response size.
   */
  private static class GenericResponseWrapper extends HttpServletResponseWrapper {
    private ByteArrayOutputStream output;
    private int contentLength;
    private String contentType;
    private int responseCode;
    private FilterServletOutputStream filteredOut;
    private PrintWriter newWriter;

    public GenericResponseWrapper(HttpServletResponse response) {
      super(response);
      output = new ByteArrayOutputStream();
    }

    public byte[] getData() {
      if (newWriter != null) {
        newWriter.flush();
      }
      return output.toByteArray();
    }

    public int getResponseCode() {
      return responseCode;
    }

    @Override
    public ServletOutputStream getOutputStream() {
      if (null == filteredOut) {
        filteredOut = new FilterServletOutputStream(output);
      }
      return filteredOut;
    }

    @Override
    public PrintWriter getWriter() {
      if (newWriter == null) {
        OutputStreamWriter writer;
        try {
          writer = new OutputStreamWriter(getOutputStream(), getCharacterEncoding());
        } catch (UnsupportedEncodingException e) {
          log.warning(String.format("Could not write using charset %s, using default instead",
              getCharacterEncoding()));
          writer = new OutputStreamWriter(getOutputStream());
        }
        newWriter = new PrintWriter(new BufferedWriter(writer), true);
      }
      return newWriter;
    }

    @Override
    public void setContentLength(int length) {
      this.contentLength = length;
      super.setContentLength(length);
    }

    public int getContentLength() {
      return contentLength;
    }

    @Override
    public void sendError(int sc) throws IOException {
      responseCode = sc;
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      responseCode = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void setContentType(String type) {
      this.contentType = type;
      super.setContentType(type);
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public void setStatus(int sc) {
      this.responseCode = sc;
      super.setStatus(sc);
    }
  }
}
