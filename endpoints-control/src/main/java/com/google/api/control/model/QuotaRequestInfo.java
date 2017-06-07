/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.api.client.util.Clock;
import com.google.api.servicecontrol.v1.AllocateQuotaRequest;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.QuotaOperation;
import com.google.api.servicecontrol.v1.QuotaOperation.QuotaMode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Holds information about an {@code AllocateQuotaRequest} to be obtained from the HTTP layer.
 */
public class QuotaRequestInfo extends OperationInfo {
  private Map<String, Long> metricCosts;
  private String configId;
  private String clientIp;

  public QuotaRequestInfo() {
    // default constructor
    metricCosts = ImmutableMap.of();
  }

  public QuotaRequestInfo(OperationInfo o) {
    setApiKey(o.getApiKey());
    setApiKeyValid(o.isApiKeyValid());
    setConsumerProjectId(o.getConsumerProjectId());
    setOperationId(o.getOperationId());
    setOperationName(o.getOperationName());
    setReferer(o.getReferer());
    setServiceName(o.getServiceName());
  }

  public QuotaRequestInfo setMetricCosts(Map<String, Long> metricCosts) {
    this.metricCosts = metricCosts;
    return this;
  }

  public Map<String, Long> getMetricCosts() {
    return metricCosts;
  }

  public QuotaRequestInfo setConfigId(String configId) {
    this.configId = configId;
    return this;
  }

  public String getConfigId() {
    return configId;
  }

  /**
   * @return the client IP address
   */
  public String getClientIp() {
    return clientIp;
  }

  public QuotaRequestInfo setClientIp(String clientIp) {
    this.clientIp = clientIp;
    return this;
  }

  /**
   * Returns the {@link AllocateQuotaRequest} instance corresponding to this instance.
   *
   * <p>
   * The service name, operation ID and operation Name must all be set
   * <p>
   *
   * @param clock is used to determine the current timestamp
   *
   * @return an {@link AllocateQuotaRequest}
   * @throws java.lang.IllegalStateException if any required values are not set when this is called.
   */
  public AllocateQuotaRequest asQuotaRequest(Clock clock) {
    Preconditions.checkState(!Strings.isNullOrEmpty(getServiceName()),
        "a service name must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationId()),
        "an operation ID must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationName()),
        "an operation name must be set");
    Operation.Builder b = super.asOperation(clock).toBuilder();
    b.putAllLabels(getSystemLabels());

    QuotaOperation.Builder opBuilder = QuotaOperation.newBuilder()
        .setOperationId(getOperationId())
        .setMethodName(getOperationName())
        .setConsumerId(getOperationConsumerId())
        .setQuotaMode(QuotaMode.BEST_EFFORT)
        .putAllLabels(getSystemLabels());
    for (Map.Entry<String, Long> entry : metricCosts.entrySet()) {
      long cost = entry.getValue();
      opBuilder.addQuotaMetrics(MetricValueSet.newBuilder()
          .setMetricName(entry.getKey())
          .addMetricValues(MetricValue.newBuilder()
              .setInt64Value(cost <= 0 ? 1 : cost)));
    }
    AllocateQuotaRequest.Builder builder = AllocateQuotaRequest.newBuilder()
        .setServiceName(getServiceName())
        .setAllocateOperation(opBuilder);
    if (configId != null) {
      builder.setServiceConfigId(configId);
    }
    return builder.build();
  }

  @Override
  protected Map<String, String> getSystemLabels() {
    Map<String, String> labels = super.getSystemLabels();
    if (!Strings.isNullOrEmpty(getClientIp())) {
      labels.put(SCC_CALLER_IP, getClientIp());
    }
    if (!Strings.isNullOrEmpty(getReferer())) {
      labels.put(SCC_REFERER, getReferer());
    }
    return labels;
  }
}
