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

import java.util.Map;

import com.google.api.servicecontrol.v1.LogEntry;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.Maps;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Holds information about a {@code ReportRequest} to be obtained from the HTTP layer.
 */
public class ReportRequestInfo extends OperationInfo {
  private static final int NANOS_PER_MILLIS = 1000000;

  /**
   * ReportedPlatform enumerates the platforms that may be reported.
   */
  public enum ReportedPlatforms {
    UNKNOWN, GAE, GCE, GKE
  }

  /**
   * ReportedProtocols enumerates the protocols that may be reported.
   */
  public enum ReportedProtocols {
    UNKNOWN, HTTP, HTTP2, GRPC
  }

  /**
   * Enumerates the reported causes of errors.
   */
  public enum ErrorCause {
    internal, // error in the SCC library code
    application, // external application
    auth, // authentication error
    service_control // a service control check fails
  }

  private String apiName;
  private String apiMethod;
  private String apiVersion;
  private String authIssuer;
  private String authAudience;
  private long backendTimeMillis;
  private ErrorCause errorCause;
  private String location;
  private String logMessage;
  private String method;
  private long overheadTimeMillis;
  private ReportedPlatforms platform;
  private String producerProjectId;
  private ReportedProtocols protocol;
  private long requestSize;
  private long requestTimeMillis;
  private int responseCode;
  private long responseSize;
  private String url;

  public ReportRequestInfo() {
    // default constructor
  }

  /**
   * A copy constructor that initializes the {@code OperationInfo} fields from a base instance.
   *
   * @param o an {code OperationInfo}
   */
  public ReportRequestInfo(OperationInfo o) {
    setApiKey(o.getApiKey());
    setApiKeyValid(o.isApiKeyValid());
    setConsumerProjectId(o.getConsumerProjectId());
    setOperationId(o.getOperationId());
    setOperationName(o.getOperationName());
    setReferer(o.getReferer());
    setServiceName(o.getServiceName());
  }

  /**
   * Make a {@code LogEntry} from the instance.
   *
   * @param rules ReportingRules
   * @param ticker Ticker
   */
  public ReportRequest asReportRequest(ReportingRule rules, Ticker ticker) {
    Preconditions.checkState(Strings.isNullOrEmpty(getServiceName()));

    // Populate metrics and labels if they can be associated with a method/operation
    Operation.Builder o = asOperation(ticker).toBuilder();
    if (!Strings.isNullOrEmpty(o.getOperationId())
        && !Strings.isNullOrEmpty(o.getOperationName())) {
      Map<String, String> addedLabels = Maps.newHashMap();
      for (KnownLabels l : rules.getLabels()) {
        l.performUpdate(this, addedLabels);
      }
      KnownMetrics[] metrics = rules.getMetrics();
      for (KnownMetrics m : metrics) {
        m.performUpdate(this, o);
      }
    }

    String[] logs = rules.getLogs();
    long timestampMillis = ticker.read() / NANOS_PER_MILLIS;
    for (String l : logs) {
      o.addLogEntries(asLogEntry(l, timestampMillis));
    }

    return ReportRequest.newBuilder().addOperations(o).setServiceName(getServiceName()).build();
  }

  /**
   * Make a {@code LogEntry} from the instance.
   */
  public LogEntry.Builder asLogEntry(String name, long timestampMillis) {
    Value.Builder vb = Value.newBuilder();
    Map<String, Value> values = Maps.newHashMap();
    values.put("http_response_code", vb.setNumberValue(getResponseCode()).build());
    values.put("timestamp", vb.setNumberValue(timestampMillis).build());
    if (getRequestSize() > 0) {
      values.put("request_size", vb.setNumberValue(getRequestSize()).build());
    }
    if (getResponseSize() > 0) {
      values.put("response_size", vb.setNumberValue(getResponseSize()).build());
    }
    if (!Strings.isNullOrEmpty(getMethod())) {
      values.put("http_method", vb.setStringValue(getMethod()).build());
    }
    if (!Strings.isNullOrEmpty(getApiName())) {
      values.put("api_name", vb.setStringValue(getApiName()).build());
    }
    if (!Strings.isNullOrEmpty(getApiMethod())) {
      values.put("api_method", vb.setStringValue(getApiMethod()).build());
    }
    if (!Strings.isNullOrEmpty(getApiKey())) {
      values.put("api_key", vb.setStringValue(getApiKey()).build());
    }
    if (!Strings.isNullOrEmpty(getProducerProjectId())) {
      values.put("producer_project_id", vb.setStringValue(getProducerProjectId()).build());
    }
    if (!Strings.isNullOrEmpty(getReferer())) {
      values.put("referer", vb.setStringValue(getReferer()).build());
    }
    if (!Strings.isNullOrEmpty(getLocation())) {
      values.put("location", vb.setStringValue(getLocation()).build());
    }
    if (!Strings.isNullOrEmpty(getLogMessage())) {
      values.put("log_message", vb.setStringValue(getLogMessage()).build());
    }
    if (!Strings.isNullOrEmpty(getUrl())) {
      values.put("url", vb.setStringValue(getUrl()).build());
    }
    if (getResponseCode() >= 400) {
      values.put("error_cause", vb.setStringValue(getErrorCause().name()).build());
    }

    Struct.Builder theStruct = Struct.newBuilder().putAllFields(values);
    return LogEntry.newBuilder().setStructPayload(theStruct).setName(name);
  }

  /**
   * @return the api name of the reported operation
   */
  public String getApiName() {
    return apiName;
  }

  public ReportRequestInfo setApiName(String apiName) {
    this.apiName = apiName;
    return this;
  }

  /**
   * @return the api method of the reported operation
   */
  public String getApiMethod() {
    return apiMethod;
  }

  public ReportRequestInfo setApiMethod(String apiMethod) {
    this.apiMethod = apiMethod;
    return this;
  }

  /**
   * @return the api version of the reported operation
   */
  public String getApiVersion() {
    return apiVersion;
  }

  public ReportRequestInfo setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  /**
   * @return the authentication issuer of the reported operation
   */
  public String getAuthIssuer() {
    return authIssuer;
  }

  public ReportRequestInfo setAuthIssuer(String authIssuer) {
    this.authIssuer = authIssuer;
    return this;
  }

  /**
   * @return the authentication audience of the reported operation
   */
  public String getAuthAudience() {
    return authAudience;
  }

  public ReportRequestInfo setAuthAudience(String authAudience) {
    this.authAudience = authAudience;
    return this;
  }

  /**
   * @return the time in milliseconds spent in the Backend during the reported operation
   */
  public long getBackendTimeMillis() {
    return backendTimeMillis;
  }

  public ReportRequestInfo setBackendTimeMillis(long backendTimeMillis) {
    this.backendTimeMillis = backendTimeMillis;
    return this;
  }

  /**
   * @return the cause of any error that occurs while making the reported operation
   */
  public ErrorCause getErrorCause() {
    return errorCause;
  }

  public ReportRequestInfo setErrorCause(ErrorCause errorCause) {
    this.errorCause = errorCause;
    return this;
  }

  /**
   * @return the compute location for of the reported operation
   */
  public String getLocation() {
    return location;
  }

  public ReportRequestInfo setLocation(String location) {
    this.location = location;
    return this;
  }

  /**
   * @return the message to log for the reported operation
   */
  public String getLogMessage() {
    return logMessage;
  }

  public ReportRequestInfo setLogMessage(String logMessage) {
    this.logMessage = logMessage;
    return this;
  }

  /**
   * @return the HTTP method of the reported operation
   */
  public String getMethod() {
    return method;
  }

  public ReportRequestInfo setMethod(String method) {
    this.method = method;
    return this;
  }

  /**
   * @return the time in milliseconds spent in the library during the reported operation
   */
  public long getOverheadTimeMillis() {
    return overheadTimeMillis;
  }

  public ReportRequestInfo setOverheadTimeMillis(long overheadTimeMillis) {
    this.overheadTimeMillis = overheadTimeMillis;
    return this;
  }

  /**
   * @return the platform on which the reported operation was made
   */
  public ReportedPlatforms getPlatform() {
    return platform;
  }

  public ReportRequestInfo setPlatform(ReportedPlatforms platform) {
    this.platform = platform;
    return this;
  }

  /**
   * @return the producer project ID for the reported operation
   */
  public String getProducerProjectId() {
    return producerProjectId;
  }

  public ReportRequestInfo setProducerProjectId(String producerProjectId) {
    this.producerProjectId = producerProjectId;
    return this;
  }

  /**
   * @return the protocol used to make the reported operation
   */
  public ReportedProtocols getProtocol() {
    return protocol;
  }

  public ReportRequestInfo setProtocol(ReportedProtocols protocol) {
    this.protocol = protocol;
    return this;
  }

  /**
   * @return the size of the reported operation's request
   */
  public long getRequestSize() {
    return requestSize;
  }

  public ReportRequestInfo setRequestSize(long requestSize) {
    this.requestSize = requestSize;
    return this;
  }

  /**
   * @return the full latency in milliseconds of the reported operation
   */
  public long getRequestTimeMillis() {
    return requestTimeMillis;
  }

  public ReportRequestInfo setRequestTimeMillis(long requestTimeMillis) {
    this.requestTimeMillis = requestTimeMillis;
    return this;
  }

  /**
   * @return the HTTP response code of the reported operation
   */
  public int getResponseCode() {
    return responseCode;
  }

  public ReportRequestInfo setResponseCode(int responseCode) {
    this.responseCode = responseCode;
    return this;
  }

  /**
   * @return the size of the response of the reported operation
   */
  public long getResponseSize() {
    return responseSize;
  }

  public ReportRequestInfo setResponseSize(long responseSize) {
    this.responseSize = responseSize;
    return this;
  }

  /**
   * @return the url of the reported operation
   */
  public String getUrl() {
    return url;
  }

  public ReportRequestInfo setUrl(String url) {
    this.url = url;
    return this;
  }
}
