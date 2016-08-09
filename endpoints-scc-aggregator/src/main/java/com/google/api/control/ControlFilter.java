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

import com.google.api.client.util.Clock;
import com.google.api.scc.model.CheckErrorInfo;
import com.google.api.scc.model.CheckRequestInfo;
import com.google.api.scc.model.MethodRegistry;
import com.google.api.scc.model.OperationInfo;
import com.google.api.scc.model.ReportRequestInfo;
import com.google.api.scc.model.ReportRequestInfo.ReportedPlatforms;
import com.google.api.scc.model.ReportRequestInfo.ReportedProtocols;
import com.google.api.scc.model.ReportingRule;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
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
 * {@link Client#check(com.google.api.servicecontrol.v1.CheckRequest)} succeeds. Successful calls
 * are 'reported' via a call to
 * {@link Client#report(com.google.api.servicecontrol.v1.ReportRequest)}
 */
public class ControlFilter implements Filter {
  private static final Logger log = Logger.getLogger(ControlFilter.class.getName());
  private static final String REFERER = "referer";
  private static final String PROJECT_ID_PARAM = "endpoints.projectId";
  private static final String SERVICE_NAME_PARAM = "endpoints.serviceName";
  private final Ticker ticker;
  private final Clock clock;
  private String projectId;
  private Client client;

  @VisibleForTesting
  public ControlFilter(@Nullable Client client, @Nullable String projectId,
      @Nullable Ticker ticker, @Nullable Clock clock) {
    this.client = client;
    setProjectId(projectId);
    this.ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.clock = clock == null ? Clock.SYSTEM : clock;
  }

  public ControlFilter() {
    this(null, null, null, null);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String configProjectId = filterConfig.getInitParameter(PROJECT_ID_PARAM);
    if (!Strings.isNullOrEmpty(configProjectId)) {
      setProjectId(configProjectId);
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
   * @throws GeneralSecurityException indicates that client was not created successufully
   * @throws IOException indicates that the client was not created successfully
   */
  protected Client createClient(String configServiceName)
      throws GeneralSecurityException, IOException {
    return new Client.Builder(configServiceName).build();
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
    timer.start();

    // Service Control is not required for this method, execute the rest of the filter chain
    MethodRegistry.Info info = ConfigFilter.getMethodInfo(request);
    if (info == null) {
      chain.doFilter(request, response);
      return;
    }

    // Execute the check, and if there is an issue, terminate the request
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    AppStruct appInfo = new AppStruct();
    appInfo.httpMethod = httpRequest.getMethod();
    appInfo.requestSize = httpRequest.getContentLength();
    appInfo.url = httpRequest.getRequestURI();
    CheckRequestInfo checkInfo = createCheckInfo(httpRequest, appInfo.url, info);
    CheckRequest checkRequest = checkInfo.asCheckRequest(clock);
    log.log(Level.FINE, String.format("Checking using %s", checkRequest));
    CheckResponse checkResponse = client.check(checkRequest);
    CheckErrorInfo errorInfo = CheckErrorInfo.convert(checkResponse);
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    if (errorInfo != CheckErrorInfo.OK) {
      // 'Send' a report
      ReportRequest reportRequest =
          createReportRequest(info, checkInfo, appInfo, ConfigFilter.getReportRule(request), timer);
      log.log(Level.FINE, String.format("sending the report request %s", reportRequest));
      client.report(reportRequest);

      // For now, assume that any error information will just be the first error detail message
      httpResponse.sendError(errorInfo.getHttpCode(),
          errorInfo.fullMessage(projectId, checkResponse.getCheckErrors(0).getDetail()));
      return;
    }

    // Add the check response, then perform the rest of the request itself
    log.log(Level.FINE, String.format("adding the check response %s", checkResponse));
    timer.appStart();

    // Execute the request in wrapper, capture the data
    GenericResponseWrapper wrapper = new GenericResponseWrapper(httpResponse);
    chain.doFilter(request, wrapper);
    timer.end();
    appInfo.responseCode = wrapper.getResponseCode();
    appInfo.responseSize =
        wrapper.getContentLength() != 0 ? wrapper.getContentLength() : wrapper.getData().length;
    ServletOutputStream out = response.getOutputStream();
    out.write(wrapper.getData());
    out.close();

    // Send a report
    ReportRequest reportRequest =
        createReportRequest(info, checkInfo, appInfo, ConfigFilter.getReportRule(request), timer);
    log.log(Level.FINE, String.format("sending the report request %s", reportRequest));
    client.report(reportRequest);
  }

  private ReportRequest createReportRequest(MethodRegistry.Info info, CheckRequestInfo checkInfo,
      AppStruct appInfo, ReportingRule rules, LatencyTimer timer) {
    return new ReportRequestInfo(checkInfo)
        .setApiMethod(info.getSelector())
        .setLocation("")
        .setMethod(appInfo.httpMethod)
        .setOverheadTimeMillis(timer.getOverheadTimeMillis())
        .setPlatform(ReportedPlatforms.GAE) // TODO: fill this in correctly
        .setProducerProjectId(projectId) // TODO: confirm that this correct value to use here
        .setProtocol(ReportedProtocols.HTTP)
        .setRequestSize(appInfo.requestSize)
        .setRequestTimeMillis(timer.getRequestTimeMillis())
        .setResponseCode(appInfo.responseCode)
        .setResponseSize(appInfo.responseSize)
        .setUrl(appInfo.url)
        .asReportRequest(rules, clock);
  }

  private CheckRequestInfo createCheckInfo(HttpServletRequest request, String uri,
      MethodRegistry.Info info) {
    String serviceName = ConfigFilter.getService(request).getName();
    String apiKey = findApiKeyParam(request, info);
    if (Strings.isNullOrEmpty(apiKey)) {
      apiKey = findApiKeyHeader(request, info);
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

  private static class LatencyTimer {
    private static final int NANOS_PER_MILLIS = 1000000;
    private static final long NOT_STARTED = -1L;
    private Ticker ticker;
    private long start = NOT_STARTED;
    private long appStart = NOT_STARTED;
    private long end = NOT_STARTED;

    private LatencyTimer(Ticker ticker) {
      this.ticker = ticker;
    }

    void start() {
      start = ticker.read();
    }

    void appStart() {
      appStart = ticker.read();
    }

    void end() {
      end = ticker.read();
    }

    long getRequestTimeMillis() {
      if (start == NOT_STARTED) {
        return NOT_STARTED;
      }
      if (end == NOT_STARTED) {
        return NOT_STARTED;
      }
      return (end - start) / NANOS_PER_MILLIS;
    }

    long getOverheadTimeMillis() {
      if (start == NOT_STARTED) {
        return NOT_STARTED;
      }
      if (appStart == NOT_STARTED) {
        return NOT_STARTED;
      }
      return (appStart - start) / NANOS_PER_MILLIS;
    }
  }

  /**
   * FilterServletOutputStream is a ServletOutputStream that allows us to accurately count the
   * response size.
   */
  private static class FilterServletOutputStream extends ServletOutputStream {
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
        newWriter = new PrintWriter(getOutputStream(), true);
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
