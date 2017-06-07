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

import com.google.api.servicecontrol.v1.AllocateQuotaResponse;
import com.google.api.servicecontrol.v1.QuotaError;
import com.google.api.servicecontrol.v1.QuotaError.Code;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;

/**
 * CheckErrorInfo translates the {@code CheckError} received in a {@code CheckResponse} to HTTP
 * codes and messages.
 */
public enum QuotaErrorInfo {
  OK(
      Code.UNSPECIFIED,
      HttpServletResponse.SC_OK,
      ""),
  RESOURCE_EXHAUSTED(
      Code.RESOURCE_EXHAUSTED,
      429,
      "Quota allocation failed."),
  PROJECT_SUSPENDED(
      Code.PROJECT_SUSPENDED,
      HttpServletResponse.SC_FORBIDDEN,
      "Project suspended."),
  SERVICE_NOT_ENABLED(
      Code.SERVICE_NOT_ENABLED,
      HttpServletResponse.SC_FORBIDDEN,
      "API {service_name} is not available for the project."),
  BILLING_NOT_ACTIVE(
      Code.BILLING_NOT_ACTIVE,
      HttpServletResponse.SC_FORBIDDEN,
      "API {service_name} has billing disabled. Please enable it."),
  PROJECT_DELETED(
      Code.PROJECT_DELETED,
      HttpServletResponse.SC_BAD_REQUEST,
      "Client project not valid. Please pass a valid project."),
  PROJECT_INVALID(
      Code.PROJECT_INVALID,
      HttpServletResponse.SC_BAD_REQUEST,
      "Client project not valid. Please pass a valid project."),
  IP_ADDRESS_BLOCKED(
      Code.IP_ADDRESS_BLOCKED,
      HttpServletResponse.SC_FORBIDDEN,
      "IP address blocked."),
  REFERER_BLOCKED(
      Code.REFERER_BLOCKED,
      HttpServletResponse.SC_FORBIDDEN,
      "Referer blocked."),
  CLIENT_APP_BLOCKED(
      Code.CLIENT_APP_BLOCKED,
      HttpServletResponse.SC_FORBIDDEN,
      "Client app blocked."),
  API_KEY_INVALID(
      Code.API_KEY_INVALID,
      HttpServletResponse.SC_BAD_REQUEST,
      "API key not valid. Please pass a valid API key."),
  API_KEY_EXPIRED(
      Code.API_KEY_EXPIRED,
      HttpServletResponse.SC_BAD_REQUEST,
      "API key expired. Please renew the API key."),
  PROJECT_STATUS_UNAVAILABLE(
      Code.PROJECT_STATUS_UNAVAILABLE,
      HttpServletResponse.SC_OK,
      ""),
  SERVICE_STATUS_UNAVAILABLE(
      Code.SERVICE_STATUS_UNAVAILABLE,
      HttpServletResponse.SC_OK,
      ""),
  BILLING_STATUS_UNAVAILABLE(
      Code.BILLING_STATUS_UNAVAILABLE,
      HttpServletResponse.SC_OK,
      ""),
  QUOTA_SYSTEM_UNAVAILABLE(
      Code.QUOTA_SYSTEM_UNAVAILABLE,
      HttpServletResponse.SC_OK,
      ""),
  UNKNOWN(
      Code.UNRECOGNIZED,
      HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
      "Request blocked due to unsupported block reason {detail}");

  private final QuotaError.Code code;
  private final int httpCode;
  private final String message;
  private static final Map<QuotaError.Code, QuotaErrorInfo> CONVERSION;

  static {
    CONVERSION = Maps.newHashMap();
    for (QuotaErrorInfo i : values()) {
      if (i.code != QuotaError.Code.UNRECOGNIZED) {
        CONVERSION.put(i.code, i);
      }
    }
  }

  /**
   * CheckErrorInfo translates {@code CheckError} to HTTP status messages.
   *
   * @param code the CheckError code
   * @param httpCode the http status code to use
   * @param message the http status message
   */
  QuotaErrorInfo(QuotaError.Code code, int httpCode, String message) {
    this.code = code;
    this.httpCode = httpCode;
    this.message = message;
  }

  /**
   * Expands {@code message} with the project Id and detail where necessary
   * @param projectId the cloud project Id
   * @param detail the error detail
   * @return a string containing the expanded message
   */
  public String fullMessage(@Nullable String projectId, @Nullable String detail) {
    projectId = Strings.nullToEmpty(projectId);
    detail = Strings.nullToEmpty(detail);
    return message.replaceAll("\\{project_id\\}", projectId).replaceAll("\\{detail\\}", detail);
  }

  /**
   * @return the unexpanded error message
   */
  public String getMessage() {
    return this.message;
  }

  /**
   * @return the equivalent http status code
   */
  public int getHttpCode() {
    return httpCode;
  }

  /**
   * @return {@code true} if the error should be returned to the user
   */
  public boolean isReallyError() {
    return httpCode != HttpServletResponse.SC_OK;
  }

  /**
   * Determines the {@link QuotaErrorInfo} corresponding to response.
   *
   * @param response a response from a {@code CheckRequest}
   * @return {@code CheckErrorInfo}
   */
  public static QuotaErrorInfo convert(@Nullable AllocateQuotaResponse response) {
    if (response == null) {
      return SERVICE_STATUS_UNAVAILABLE;
    }
    if (response.getAllocateErrorsCount() == 0) {
      return OK;
    }

    // For now, only examine the first error code
    QuotaErrorInfo result = CONVERSION.get(response.getAllocateErrors(0).getCode());
    if (result == null) {
      return QuotaErrorInfo.UNKNOWN;
    } else {
      return result;
    }
  }
}
