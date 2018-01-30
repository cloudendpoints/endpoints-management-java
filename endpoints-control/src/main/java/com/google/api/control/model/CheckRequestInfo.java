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

import com.google.api.client.util.Clock;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.Operation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Map;

/**
 * Holds information about a {@code CheckRequest} to be obtained from the HTTP layer.
 */
public class CheckRequestInfo extends OperationInfo {
  @VisibleForTesting
  static final String ANDROID_PACKAGE_LABEL = "servicecontrol.googleapis.com/android_package_name";
  @VisibleForTesting
  static final String ANDROID_CERTIFICATE_FINGERPRINT_LABEL =
      "servicecontrol.googleapis.com/android_cert_fingerprint";
  @VisibleForTesting
  static final String IOS_BUNDLE_ID = "servicecontrol.googleapis.com/ios_bundle_id";

  private String clientIp;
  private String androidPackageName;
  private String androidCertificateFingerprint;
  private String iosBundleId;

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

  public String getAndroidPackageName() {
    return androidPackageName;
  }

  public CheckRequestInfo setAndroidPackageName(String androidPackageName) {
    this.androidPackageName = androidPackageName;
    return this;
  }

  public String getAndroidCertificateFingerprint() {
    return androidCertificateFingerprint;
  }

  public CheckRequestInfo setAndroidCertificateFingerprint(String androidCertificateFingerprint) {
    this.androidCertificateFingerprint = androidCertificateFingerprint;
    return this;
  }

  public String getIosBundleId() {
    return iosBundleId;
  }

  public CheckRequestInfo setIosBundleId(String iosBundleId) {
    this.iosBundleId = iosBundleId;
    return this;
  }

  /**
   * Returns the {@link CheckRequest} instance corresponding to this instance.
   *
   * <p>
   * The service name, operation ID and operation Name must all be set
   * <p>
   *
   * @param clock is used to determine the current timestamp
   *
   * @return a {@link CheckRequest}
   * @throws java.lang.IllegalStateException if any required values are not set when this is called.
   */
  public CheckRequest asCheckRequest(Clock clock) {
    Preconditions.checkState(!Strings.isNullOrEmpty(getServiceName()),
        "a service name must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationId()),
        "an operation ID must be set");
    Preconditions.checkState(!Strings.isNullOrEmpty(getOperationName()),
        "an operation name must be set");
    Operation.Builder b = super.asOperation(clock).toBuilder();
    b.putAllLabels(getSystemLabels());

    return CheckRequest.newBuilder().setServiceName(getServiceName()).setOperation(b).build();
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
    if (!Strings.isNullOrEmpty(getAndroidPackageName())) {
      labels.put(ANDROID_PACKAGE_LABEL, getAndroidPackageName());
    }
    if (!Strings.isNullOrEmpty(getAndroidCertificateFingerprint())) {
      labels.put(ANDROID_CERTIFICATE_FINGERPRINT_LABEL, getAndroidCertificateFingerprint());
    }
    if (!Strings.isNullOrEmpty(getIosBundleId())) {
      labels.put(IOS_BUNDLE_ID, getIosBundleId());
    }
    return labels;
  }
}
