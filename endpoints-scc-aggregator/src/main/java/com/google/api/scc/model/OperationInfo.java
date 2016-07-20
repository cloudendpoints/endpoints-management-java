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

import com.google.api.servicecontrol.v1.Operation;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.protobuf.Timestamp;

/**
 * Holds basic information about an operation to be obtained from the HTTP layer.
 */
public class OperationInfo {
  private boolean apiKeyValid;
  private String apiKey;
  private String consumerProjectId;
  private String operationId;
  private String operationName;
  private String referer;
  private String serviceName;

  /**
   * Returns the {@link Operation} instance corresponding to this instance.
   *
   * @param Ticker is used to determine the current timestamp
   *
   * @return a {@link Operation}
   */
  public Operation asOperation(Ticker ticker) {
    Operation.Builder b = Operation.newBuilder();
    Timestamp now = Timestamps.now(ticker);
    b.setStartTime(now).setEndTime(now);
    if (!Strings.isNullOrEmpty(operationId)) {
      b.setOperationId(operationId);
    }
    if (!Strings.isNullOrEmpty(operationName)) {
      b.setOperationName(operationName);
    }
    if (!Strings.isNullOrEmpty(apiKey) && apiKeyValid) {
      b.setConsumerId("api_key:" + apiKey);
    } else if (!Strings.isNullOrEmpty(consumerProjectId)) {
      b.setConsumerId("project:" + consumerProjectId);
    }
    return b.build();
  }

  /**
   * @return the API key
   */
  public String getApiKey() {
    return apiKey;
  }

  public OperationInfo setApiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  /**
   * @return {@code} true if the API key is valid, otherwise false
   */
  public boolean isApiKeyValid() {
    return apiKeyValid;
  }

  public OperationInfo setApiKeyValid(boolean apiKeyValid) {
    this.apiKeyValid = apiKeyValid;
    return this;
  }

  /**
   * @return the consumer project ID
   */
  public String getConsumerProjectId() {
    return consumerProjectId;
  }

  public OperationInfo setConsumerProjectId(String consumerProjectId) {
    this.consumerProjectId = consumerProjectId;
    return this;
  }

  /**
   * @return the operation ID
   */
  public String getOperationId() {
    return operationId;
  }

  public OperationInfo setOperationId(String operationId) {
    this.operationId = operationId;
    return this;
  }

  /**
   * @return the operation Name
   */
  public String getOperationName() {
    return operationName;
  }

  public OperationInfo setOperationName(String operationName) {
    this.operationName = operationName;
    return this;
  }

  /**
   * @return the requests' referer header
   */
  public String getReferer() {
    return referer;
  }

  public OperationInfo setReferer(String referer) {
    this.referer = referer;
    return this;
  }

  /**
   * @return the service name
   */
  public String getServiceName() {
    return serviceName;
  }

  public OperationInfo setServiceName(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }
}
