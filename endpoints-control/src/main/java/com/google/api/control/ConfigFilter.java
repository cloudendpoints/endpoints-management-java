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

import com.google.api.Service;
import com.google.api.config.ServiceConfigException;
import com.google.api.control.model.MethodRegistry;
import com.google.api.control.model.MethodRegistry.Info;
import com.google.api.control.model.ReportingRule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ConfigFilter used to load the {@code Service} and associated objects and make them available
 */
public class ConfigFilter implements Filter {
  private static final Logger log = Logger.getLogger(ConfigFilter.class.getName());
  private static final String ATTRIBUTE_ROOT = ConfigFilter.class.getName();

  @VisibleForTesting
  static final String METHOD_INFO_ATTRIBUTE = ATTRIBUTE_ROOT + ".method_info";

  @VisibleForTesting
  static final String SERVICE_ATTRIBUTE = ATTRIBUTE_ROOT + ".service";

  @VisibleForTesting
  static final String SERVICE_NAME_ATTRIBUTE = ATTRIBUTE_ROOT + ".service_name";

  @VisibleForTesting
  static final String REGISTRY_ATTRIBUTE = ATTRIBUTE_ROOT + ".registry";

  @VisibleForTesting
  static final String REPORTING_ATTRIBUTE = ATTRIBUTE_ROOT + ".reporting";

  private Service theService;
  private Loader loader;
  private MethodRegistry registry;
  private ReportingRule rule;

  /**
   * {@code Loader} specifies a method for loading a {@code Service} instance.
   */
  public interface Loader {
    /**
     * @return the loaded {@link Service}
     * @throws IOException if on any errors that occur while loading the service.
     */
    Service load() throws IOException;
  }

  /**
   * @param loader the used to load the service instance
   */
  public ConfigFilter(Loader loader) {
    Preconditions.checkNotNull(loader, "The loader must be non-null");
    this.loader = loader;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    try {
      theService = this.loader.load();
      rule = ReportingRule.fromService(theService);
      registry = new MethodRegistry(theService);
    } catch (IOException | ServiceConfigException e) {
      log.log(Level.SEVERE, "Failed to load service: %s", e);
      theService = null;
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (theService == null) {
      log.log(Level.WARNING,
          "Rejecting this API request due to config loading error.");
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    } else {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      httpRequest.setAttribute(SERVICE_ATTRIBUTE, theService);
      httpRequest.setAttribute(SERVICE_NAME_ATTRIBUTE, theService.getName());
      httpRequest.setAttribute(REGISTRY_ATTRIBUTE, registry);
      httpRequest.setAttribute(REPORTING_ATTRIBUTE, rule);
      log.log(Level.FINE,  String.format("Added service %s, and associated attributes to the request", theService));

      // Determine if service control is required
      String uri = httpRequest.getRequestURI();
      String method = httpRequest.getMethod();
      Info info = registry.lookup(method, uri);
      if (info != null) {
        httpRequest.setAttribute(METHOD_INFO_ATTRIBUTE, info);
      } else {
        log.log(Level.FINE, "did not add method info to the request");
      }
    }
    chain.doFilter(request, response);
  }

  /**
   * @param req a {@code ServletRequest}
   * @return the {@code MethodRegistry} added or {@code null} if its not present
   */
  public static MethodRegistry getRegistry(ServletRequest req) {
    HttpServletRequest httpRequest = (HttpServletRequest) req;
    return (MethodRegistry) httpRequest.getAttribute(REGISTRY_ATTRIBUTE);
  }

  /**
   * @param req a {@code ServletRequest}
   * @return the {@code Service} added or {@code null} if its not present
   */
  public static Service getService(ServletRequest req) {
    HttpServletRequest httpRequest = (HttpServletRequest) req;
    return (Service) httpRequest.getAttribute(SERVICE_ATTRIBUTE);
  }

  /**
   * @param req a {@code ServletRequest}
   * @return the service name added or {@code null} if its not present
   */
  public static String getServiceName(ServletRequest req) {
    HttpServletRequest httpRequest = (HttpServletRequest) req;
    return (String) httpRequest.getAttribute(SERVICE_NAME_ATTRIBUTE);
  }

  /**
   * @param req a {@code ServletRequest}
   * @return the {@code ReportingRule} added or {@code null} if its not present
   */
  public static ReportingRule getReportRule(ServletRequest req) {
    HttpServletRequest httpRequest = (HttpServletRequest) req;
    return (ReportingRule) httpRequest.getAttribute(REPORTING_ATTRIBUTE);
  }

  /**
   * @param req a {@code ServletRequest}
   * @return the {@code MethodRegistry.Info} specifying the service method information or
   *         {@code null} if its not present
   */
  public static MethodRegistry.Info getMethodInfo(ServletRequest req) {
    HttpServletRequest httpRequest = (HttpServletRequest) req;
    return (MethodRegistry.Info) httpRequest.getAttribute(METHOD_INFO_ATTRIBUTE);
  }

  @Override
  public void destroy() {
    // unused
  }
}
