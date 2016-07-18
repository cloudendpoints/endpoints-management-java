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

import java.util.Map;

import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.Operation;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.Maps;

/**
 * Holds information about a {@code CheckRequest} to be obtained from the HTTP layer.
 */
public class CheckRequestInfo extends OperationInfo {
  private String clientIp;
  public static final String SCC_CALLER_IP = "servicecontrol.googleapis.com/caller_ip";
  public static final String SCC_USER_AGENT = "servicecontrol.googleapis.com/user_agent";
  public static final String SCC_REFERER = "servicecontrol.googleapis.com/referer";
  public static final String USER_AGENT = "service-control-client/java";

  public CheckRequestInfo() {
    // default constructor
  }

  public CheckRequestInfo(OperationInfo o) {
    setApiKey(o.getApiKey());
    setApiKeyValid(o.isApiKeyValid());
    setConsumerProjectId(o.getConsumerProjectId());
    setOperationId(o.getOperationId());
    setOperationName(o.getOperationName());
    setReferer(o.getReferer());
    setServiceName(o.getServiceName());
  }

  /**
   * @return the client IP address
   */
  public String getClientIp() {
    return clientIp;
  }

  public CheckRequestInfo setClientIp(String clientIp) {
    this.clientIp = clientIp;
    return this;
  }

  /**
   * Returns the {@link CheckRequest} instance corresponding to this instance.
   *
   * <p>
   * The service name, operation ID and operation Name must all be set
   * <p>
   *
   * @param Ticker is used to determine the current timestamp
   *
   * @return a {@link CheckRequest}
   * @throws {@link IllegalStateException} if any required values are not set when this is called.
   */
  public CheckRequest asCheckRequest(Ticker ticker) {
    Preconditions.checkState(!Strings.isNullOrEmpty(getServiceName()),
        "a service name must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationId()),
        "an operation ID must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationName()),
        "an operation name must be set");
    Operation.Builder b = super.asOperation(ticker).toBuilder();
    Map<String, String> labels = Maps.newHashMap();
    labels.put(SCC_USER_AGENT, USER_AGENT);
    if (!Strings.isNullOrEmpty(getReferer())) {
      labels.put(SCC_REFERER, getReferer());
    }
    if (!Strings.isNullOrEmpty(getClientIp())) {
      labels.put(SCC_CALLER_IP, getClientIp());
    }
    b.putAllLabels(labels);

    return CheckRequest.newBuilder().setServiceName(getServiceName()).setOperation(b).build();
  }
}
