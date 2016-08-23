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

import com.google.api.servicecontrol.v1.CheckError;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;

/**
 * CheckErrorInfo translates the {@code CheckError} received in a {@code CheckResponse} to HTTP
 * codes and messages.
 */
public enum CheckErrorInfo {
  OK(CheckError.Code.UNRECOGNIZED, HttpServletResponse.SC_OK, "", false),

  NAMESPACE_LOOKUP_UNAVAILABLE(CheckError.Code.NAMESPACE_LOOKUP_UNAVAILABLE,
      HttpServletResponse.SC_OK, "", false),

  SERVICE_STATUS_UNAVAILABLE(CheckError.Code.SERVICE_STATUS_UNAVAILABLE, HttpServletResponse.SC_OK,
      "", false),

  BILLING_STATUS_UNAVAILABLE(CheckError.Code.BILLING_STATUS_UNAVAILABLE, HttpServletResponse.SC_OK,
      "", false),

  QUOTA_CHECK_UNAVAILABLE(CheckError.Code.QUOTA_CHECK_UNAVAILABLE, HttpServletResponse.SC_OK, "",
      false),

  NOT_FOUND(CheckError.Code.NOT_FOUND, HttpServletResponse.SC_BAD_REQUEST,
      "Client project not found. Please pass a valid project", false),

  API_KEY_NOT_FOUND(CheckError.Code.API_KEY_NOT_FOUND, HttpServletResponse.SC_BAD_REQUEST,
      "API key not found. Please pass a valid API key", true),

  API_KEY_EXPIRED(CheckError.Code.API_KEY_EXPIRED, HttpServletResponse.SC_BAD_REQUEST,
      "API key expired.  Please renew the API key", true),

  API_KEY_INVALID(CheckError.Code.API_KEY_INVALID, HttpServletResponse.SC_BAD_REQUEST,
      "API key not valid. Please pass a valid API key", true),

  SERVICE_NOT_ACTIVATED(CheckError.Code.SERVICE_NOT_ACTIVATED, HttpServletResponse.SC_FORBIDDEN,
      "{detail} Please enable the project for {project_id}", false),

  PERMISSION_DENIED(CheckError.Code.PERMISSION_DENIED, HttpServletResponse.SC_FORBIDDEN,
      "Permission denied: {detail}",
      false),

  IP_ADDRESS_BLOCKED(CheckError.Code.IP_ADDRESS_BLOCKED, HttpServletResponse.SC_FORBIDDEN,
      "{detail}", false),

  REFERER_BLOCKED(CheckError.Code.CLIENT_APP_BLOCKED, HttpServletResponse.SC_FORBIDDEN, "{detail}",
      false),

  CLIENT_APP_BLOCKED(CheckError.Code.CLIENT_APP_BLOCKED, HttpServletResponse.SC_FORBIDDEN,
      "{detail}", false),

  PROJECT_DELETED(CheckError.Code.PROJECT_DELETED, HttpServletResponse.SC_FORBIDDEN,
      "Project {project_id} has been deleted", false),

  PROJECT_INVALID(CheckError.Code.PROJECT_INVALID, HttpServletResponse.SC_BAD_REQUEST,
      "Client Project is not valid.  Please pass a valid project", false),

  VISIBILITY_DENIED(CheckError.Code.VISIBILITY_DENIED, HttpServletResponse.SC_BAD_REQUEST,
      "Project {project_id} has no visibility access to the service", false),

  BILLING_DISABLED(CheckError.Code.BILLING_DISABLED, HttpServletResponse.SC_BAD_REQUEST,
      "Project {project_id} has billing disabled. Please enable it", false),

  UNKNOWN(CheckError.Code.UNRECOGNIZED, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
      "Request blocked due to unsupported block reason {detail}", false),

  API_KEY_NOT_PROVIDED(CheckError.Code.UNRECOGNIZED, HttpServletResponse.SC_UNAUTHORIZED,
      "Method doesn't allow callers without established identity."
          + " Please use an API key or other form of API consumer identity to call this API.",
      true);


  private final CheckError.Code code;
  private final int httpCode;
  private final String message;
  private final boolean isApiKeyError;
  private static final Map<CheckError.Code, CheckErrorInfo> CONVERSION;

  static {
    CONVERSION = Maps.newHashMap();
    for (CheckErrorInfo i : values()) {
      if (i.code != CheckError.Code.UNRECOGNIZED) {
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
   * @param isApiKeyError
   */
  CheckErrorInfo(CheckError.Code code, int httpCode, String message, boolean isApiKeyError) {
    this.code = code;
    this.httpCode = httpCode;
    this.message = message;
    this.isApiKeyError = isApiKeyError;
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
   * @return {@code true} if original code corresponds to a API key issue, otherwise {@code false}
   */
  public boolean isApiKeyError() {
    return isApiKeyError;
  }

  /**
   * Determines the {@link CheckErrorInfo} corresponding to response.
   *
   * @param response a response from a {@code CheckRequest}
   * @return {@code CheckErrorInfo}
   */
  public static final CheckErrorInfo convert(@Nullable CheckResponse response) {
    if (response == null) {
      return SERVICE_STATUS_UNAVAILABLE;
    }
    if (response.getCheckErrorsCount() == 0) {
      return OK;
    }

    // For now, only examine the first error code
    CheckErrorInfo result = CONVERSION.get(response.getCheckErrors(0).getCode());
    if (result == null) {
      return CheckErrorInfo.UNKNOWN;
    } else {
      return result;
    }
  }
}
