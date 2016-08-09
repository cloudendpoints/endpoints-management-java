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

import com.google.api.client.util.Clock;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.common.base.Strings;
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
  public Operation asOperation(Clock clock) {
    Operation.Builder b = Operation.newBuilder();
    b.setImportance(Importance.LOW);
    Timestamp now = Timestamps.now(clock);
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
