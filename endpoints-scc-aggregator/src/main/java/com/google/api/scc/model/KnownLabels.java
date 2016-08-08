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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_GATEWAY_TIMEOUT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static javax.servlet.http.HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.api.LabelDescriptor;
import com.google.api.LabelDescriptor.ValueType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.rpc.Code;

/**
 * KnownLabels enumerates the well-known labels and allows them to be added to the ReportRequest's
 * labels.
 */
public enum KnownLabels {
  CREDENTIAL_ID("/credential_id", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (!Strings.isNullOrEmpty(info.getApiKey())) {
        labels.put(name, String.format("apiKey:%s", info.getApiKey()));
      } else if (!Strings.isNullOrEmpty(info.getAuthIssuer())) {
        BaseEncoding encoder = BaseEncoding.base64Url();
        if (!Strings.isNullOrEmpty(info.getAuthAudience())) {
          labels.put(name,
              String.format("jwtAuth:issuer=%s&audience=%s",
                  encoder.encode(info.getAuthIssuer().getBytes(StandardCharsets.UTF_8)),
                  encoder.encode(info.getAuthAudience().getBytes(StandardCharsets.UTF_8))));
        } else {
          labels.put(name, String.format("jwtAuth:issuer=%s",
              encoder.encode(info.getAuthIssuer().getBytes(StandardCharsets.UTF_8))));
        }
      }
    }
  }),

  END_USER("/end_user", ValueType.STRING, Kind.USER, null),

  END_USER_COUNTRY("/end_user_country", ValueType.STRING, Kind.USER, noUpdate()),

  ERROR_TYPE("/error_type", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (info.getResponseCode() > 0) {
        labels.put(name, String.format("%dXX", (info.getResponseCode() / 100)));
      }
    }
  }),

  PROTOCOL("/protocol", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (info.getProtocol() != null) {
        labels.put(name, info.getProtocol().name());
      } else {
        labels.put(name, ReportRequestInfo.ReportedProtocols.UNKNOWN.name());
      }
    }
  }),

  REFERER("/referer", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (!Strings.isNullOrEmpty(info.getReferer())) {
        labels.put(name, info.getReferer());
      }
    }
  }),

  RESPONSE_CODE("/response_code", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      labels.put(name, String.format("%d", info.getResponseCode()));
    }
  }),

  RESPONSE_CODE_CLASS("/response_code", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (info.getResponseCode() > 0) {
        labels.put(name, String.format("%dXX", (info.getResponseCode() / 100)));
      }
    }
  }),

  STATUS_CODE("/status_code", ValueType.STRING, Kind.USER, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (info.getResponseCode() > 0) {
        labels.put(name, String.format("%d", cannonicalCodeOf(info.getResponseCode())));
      }
    }
  }),

  GAE_CLONE_ID("appengine.googleapis.com/clone_id", ValueType.STRING, Kind.USER, noUpdate()),

  GAE_MODULE_ID("appengine.googleapis.com/module_id", ValueType.STRING, Kind.USER, noUpdate()),

  GAE_REPLICA_INDEX("appengine.googleapis.com/replica_index", ValueType.STRING, Kind.USER,
      noUpdate()),

  GAE_VERSION_ID("appengine.googleapis.com/version_id", ValueType.STRING, Kind.USER, noUpdate()),

  GCP_LOCATION("cloud.googleapis.com/location", ValueType.STRING, Kind.SYSTEM, new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      if (!Strings.isNullOrEmpty(info.getLocation())) {
        labels.put(name, info.getLocation());
      }
    }
  }),

  GCP_PROJECT("cloud.googleapis.com/project", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  GCP_REGION("cloud.googleapis.com/region", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  GCP_RESOURCE_ID("cloud.googleapis.com/resource_id", ValueType.STRING, Kind.USER, noUpdate()),

  GCP_RESOURCE_TYPE("cloud.googleapis.com/resource_type", ValueType.STRING, Kind.USER, noUpdate()),

  GCP_SERVICE("cloud.googleapis.com/service", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  GCP_ZONE("cloud.googleapis.com/zone", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  GCP_UID("cloud.googleapis.com/uid", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  SVC_API_METHOD("serviceruntime.googleapis.com/api_method", ValueType.STRING, Kind.SYSTEM,
      new Update() {
        @Override
        public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
          if (!Strings.isNullOrEmpty(info.getApiMethod())) {
            labels.put(name, info.getApiMethod());
          }
        }
      }),

  SVC_API_VERSION("serviceruntime.googleapis.com/api_version", ValueType.STRING, Kind.SYSTEM,
      new Update() {
        @Override
        public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
          if (!Strings.isNullOrEmpty(info.getApiVersion())) {
            labels.put(name, info.getApiVersion());
          }
        }
      }),

  SCC_PLATFORM("servicecontrol.googleapis.com/platform", ValueType.STRING, Kind.SYSTEM,
      new Update() {
        @Override
        public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
          if (info.getPlatform() != null) {
            labels.put(name, info.getPlatform().name());
          } else {
            labels.put(name, ReportRequestInfo.ReportedPlatforms.UNKNOWN.name());
          }
        }
      }),

  SCC_REFERER("servicecontrol.googleapis.com/referer", ValueType.STRING, Kind.SYSTEM, noUpdate()),

  SCC_SERVICE_AGENT("servicecontrol.googleapis.com/service_agent", ValueType.STRING, Kind.SYSTEM,
      new Update() {
        @Override
        public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
          labels.put(name, SERVICE_AGENT);
        }
      }),

  SCC_USER_AGENT("servicecontrol.googleapis.com/user_agent", ValueType.STRING, Kind.SYSTEM,
      new Update() {
        @Override
        public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
          labels.put(name, USER_AGENT);
        }
      });

  /**
   * A null implementation of {@link Update}
   */
  public static final Update NO_UPDATE = new Update() {
    @Override
    public void update(String name, ReportRequestInfo info, Map<String, String> labels) {
      // null implementation, does nothing
    }
  };

  /**
   * The user agent to record in report requests
   *
   * At the moment (2016/08/02), the user agent must be either 'ESP' or 'ESF'
   */
  public static final String USER_AGENT = "ESP";

  /**
   * The service agent to record in report requests
   */
  public static final String SERVICE_AGENT = USER_AGENT + "/0.1.0";

  private String name;
  private LabelDescriptor.ValueType type;
  private Update updater;
  private Kind kind;

  private KnownLabels(String name, LabelDescriptor.ValueType type, Kind kind, Update updater) {
    this.name = name;
    this.type = type;
    this.kind = kind;
    this.updater = updater;
  }

  public String getName() {
    return name;
  }

  public LabelDescriptor.ValueType getType() {
    return type;
  }

  public Update getUpdater() {
    return updater;
  }

  /**
   * Determines if {@code d} matches this {@code KnownLabel} instance.
   *
   * @param d a {@code LabelDescriptor}
   */
  public boolean matches(LabelDescriptor d) {
    return name.equals(d.getKey()) && d.getValueType() == this.type;
  }

  /**
   * Updates {@code labels} with request data
   *
   * @param info contains request data to be reported
   * @param labels the labels to be updated with request data
   */
  public void performUpdate(ReportRequestInfo info, Map<String, String> labels) {
    if (updater != null) {
      updater.update(name, info, labels);
    }
  }

  /**
   * Determines if the given label descriptor is supported
   *
   * @param d a {@code LabelDescriptor}
   * @return {@code true} if the label descriptor is supported, otherwise false
   */
  public static boolean isSupported(LabelDescriptor d) {
    for (KnownLabels l : values()) {
      if (l.matches(d)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Update defines a function that allows a {@code ReportRequestInfo} to be used to update labels
   * to be added to a {@code ReportRequest}
   */
  public static interface Update {
    /**
     * Updates the label in {@code labels} with the given {@code name} from {@code info}.
     *
     * @param name the name of a label to update
     * @param info the {@code ReportRequestInfo} from which to update the labels
     * @param labels the map of labels
     */
    void update(String name, ReportRequestInfo info, Map<String, String> labels);
  }

  /**
   * Kind enumerates the kinds of labels that may be reported.
   */
  private static enum Kind {
    USER, SYSTEM
  }

  private static final Map<Integer, Integer> CANNONICAL_CODES =
      ImmutableMap
          .<Integer, Integer>builder()
      .put(SC_OK, Code.OK_VALUE)
      .put(SC_BAD_REQUEST, Code.INVALID_ARGUMENT_VALUE)
      .put(SC_UNAUTHORIZED, Code.UNAUTHENTICATED_VALUE)
      .put(SC_FORBIDDEN, Code.PERMISSION_DENIED_VALUE)
      .put(SC_NOT_FOUND, Code.NOT_FOUND_VALUE)
      .put(SC_CONFLICT, Code.ABORTED_VALUE)
      .put(SC_PRECONDITION_FAILED, Code.FAILED_PRECONDITION_VALUE)
      .put(SC_REQUESTED_RANGE_NOT_SATISFIABLE, Code.OUT_OF_RANGE_VALUE)
      .put(429, Code.RESOURCE_EXHAUSTED_VALUE)
      .put(499, Code.CANCELLED_VALUE)
      .put(SC_INTERNAL_SERVER_ERROR, Code.INTERNAL_VALUE)
      .put(SC_GATEWAY_TIMEOUT, Code.DEADLINE_EXCEEDED_VALUE)
      .put(SC_NOT_IMPLEMENTED, Code.UNIMPLEMENTED_VALUE)
      .put(SC_SERVICE_UNAVAILABLE, Code.UNAVAILABLE_VALUE)
      .build();

  private static Update noUpdate() {
    return NO_UPDATE;
  }

  private static int cannonicalCodeOf(int httpCode) {
    if (CANNONICAL_CODES.containsKey(httpCode)) {
      return CANNONICAL_CODES.get(httpCode);
    }
    if (httpCode >= 200 && httpCode < 300) {
      return Code.OK_VALUE;
    } else if (httpCode >= 400 && httpCode < 500) {
      return Code.FAILED_PRECONDITION_VALUE;
    } else if (httpCode >= 500 && httpCode < 600) {
      return Code.INTERNAL_VALUE;
    } else {
      return Code.UNKNOWN_VALUE;
    }
  }
}
