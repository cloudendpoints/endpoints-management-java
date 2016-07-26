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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.api.HttpRule;
import com.google.api.Service;
import com.google.api.SystemParameter;
import com.google.api.SystemParameterRule;
import com.google.api.UsageRule;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import autovalue.shaded.com.google.common.common.base.Preconditions;

/**
 * MethodRegistry provides registry of the API methods defined by a Service.
 */
public class MethodRegistry {
  private static final Logger log = Logger.getLogger(MethodRegistry.class.getName());
  private static final String OPTIONS_VERB = "OPTIONS";
  private final Service theService;
  private final Map<String, List<Info>> infosByHttpMethod;
  private final Map<String, Info> extractedMethods;

  /**
   * @param s contains the methods to be registered
   */
  public MethodRegistry(Service s) {
    Preconditions.checkNotNull(s, "The service must be specified");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(s.getName()),
        "The service name must be specified");
    theService = s;
    infosByHttpMethod = Maps.newHashMap();
    extractedMethods = Maps.newHashMap();
    extractMethods();
  }

  /**
   * Finds the {@code Info} instance that matches {@code httpMethod} and {@code url}.
   *
   * @param httpMethod the method of a HTTP request
   * @param url the url of a HTTP request
   * @return an {@code Info} corresponding to the url and method, or null if none is found
   */
  public @Nullable Info lookup(String httpMethod, String url) {
    httpMethod = httpMethod.toLowerCase();
    if (url.startsWith("/")) {
      url = url.substring(1);
    }
    List<Info> infos = infosByHttpMethod.get(httpMethod);
    if (infos == null) {
      log.log(Level.FINE,
          String.format("no information about urls for HTTP method %s", httpMethod));
      return null;
    }
    for (Info info : infos) {
      log.log(Level.FINE, String.format("trying %s with template %s", url, info.getTemplate()));
      if (info.getTemplate().matches(url)) {
        log.log(Level.FINE, String.format("%s matched %s", url, info.getTemplate()));
        return info;
      } else {
        log.log(Level.FINE, String.format("%s did not matched %s", url, info.getTemplate()));
      }
    }

    return null;
  }

  private void extractMethods() {
    if (!theService.hasHttp()) {
      return;
    }

    List<HttpRule> rules = theService.getHttp().getRulesList();
    Set<String> allUrls = Sets.newHashSet();
    Set<String> urlsWithOptions = Sets.newHashSet();
    for (HttpRule r : rules) {
      String url = urlFrom(r);
      String httpMethod = httpMethodFrom(r);
      if (Strings.isNullOrEmpty(url) || Strings.isNullOrEmpty(httpMethod)
          || Strings.isNullOrEmpty(r.getSelector())) {
        log.log(Level.WARNING, "invalid HTTP binding detected");
        continue;
      }
      Info theMethod = getOrCreateInfo(r.getSelector());
      if (!Strings.isNullOrEmpty(r.getBody())) {
        theMethod.setBodyFieldPath(r.getBody());
      }
      if (!register(httpMethod, url, theMethod)) {
        continue;
      }
      allUrls.add(url);
      if (httpMethod.equals(OPTIONS_VERB)) {
        urlsWithOptions.add(url);
      }
    }
    allUrls.removeAll(urlsWithOptions);
    addCorsOptionSelectors(allUrls);
    updateUsage();
    updateSystemParameters();
  }

  private boolean register(String httpMethod, String url, Info theMethod) {
    try {
      PathTemplate t = PathTemplate.create(url);
      theMethod.setTemplate(t);
      List<Info> infos = infosByHttpMethod.get(httpMethod);
      if (infos == null) {
        infos = Lists.newArrayList();
        infosByHttpMethod.put(httpMethod, infos);
      }
      infos.add(theMethod);
      log.log(Level.FINE,
          String.format("registered template template %s under method %", t, httpMethod));
      return true;
    } catch (ValidationException e) {
      log.log(Level.WARNING, String.format("invalid HTTP template %s provided", url));
      return false;
    }
  }

  private void addCorsOptionSelectors(Set<String> allUrls) {
    String baseSelector = String.format("%s.%s", theService.getName(), OPTIONS_VERB);
    String optionsSelector = baseSelector;

    // ensure that no existing options selector is used
    int index = 0;
    Info info = extractedMethods.get(optionsSelector);
    while (info != null) {
      index++;
      optionsSelector = String.format("%s.%d", baseSelector, index);
      info = extractedMethods.get(optionsSelector);
    }

    Info newInfo = getOrCreateInfo(optionsSelector);
    newInfo.setAuth(false);
    newInfo.setAllowRegisteredCalls(true);
    for (String u : allUrls) {
      register(OPTIONS_VERB, u, newInfo);
    }
  }

  private void updateSystemParameters() {
    if (!theService.hasSystemParameters()) {
      return;
    }

    for (SystemParameterRule r : theService.getSystemParameters().getRulesList()) {
      Info info = extractedMethods.get(r.getSelector());
      if (info == null) {
        log.log(Level.WARNING,
            String.format("bad system parameter: no HTTP rule for %s", r.getSelector()));
      } else {
        for (SystemParameter parameter : r.getParametersList()) {
          if (Strings.isNullOrEmpty(parameter.getName())) {
            log.log(Level.WARNING,
                String.format("bad system parameter: no parameter name for %s", r.getSelector()));
            continue;
          }
          if (!Strings.isNullOrEmpty(parameter.getHttpHeader())) {
            info.addHeaderParam(parameter.getName(), parameter.getHttpHeader());
          }
          if (!Strings.isNullOrEmpty(parameter.getUrlQueryParameter())) {
            info.addHeaderParam(parameter.getName(), parameter.getUrlQueryParameter());
          }
        }
      }
    }
  }

  private void updateUsage() {
    if (!theService.hasUsage()) {
      return;
    }

    for (UsageRule r : theService.getUsage().getRulesList()) {
      Info info = extractedMethods.get(r.getSelector());
      if (info == null) {
        log.log(Level.WARNING,
            String.format("bad usage selector: no HTTP rule for %s", r.getSelector()));
      } else {
        info.setAllowRegisteredCalls(r.getAllowUnregisteredCalls());
      }
    }
  }

  private Info getOrCreateInfo(String selector) {
    Info i = extractedMethods.get(selector);
    if (i != null) {
      return i;
    }
    i = new Info(selector);
    extractedMethods.put(selector, i);
    return i;
  }

  private static final String httpMethodFrom(HttpRule r) {
    switch (r.getPatternCase()) {
      case CUSTOM:
        return r.getCustom().getKind().toLowerCase();
      case DELETE:
      case GET:
      case PATCH:
      case POST:
      case PUT:
        return r.toString().toLowerCase();
      default:
        return null;
    }
  }

  private static final String urlFrom(HttpRule r) {
    switch (r.getPatternCase()) {
      case CUSTOM:
        return r.getCustom().getKind();
      case DELETE:
        return r.getDelete();
      case GET:
        return r.getGet();
      case PATCH:
        return r.getPatch();
      case POST:
        return r.getPost();
      case PUT:
        return r.getPut();
      default:
        return null;
    }
  }

  /**
   * Consolidates information about methods defined in a Service
   */
  public static class Info {
    private static final String API_KEY_NAME = "api_key";
    private boolean auth;
    private boolean allowRegisteredCalls;
    private String selector;
    private String backendAddress;
    private String bodyFieldPath;
    private Map<String, List<String>> urlQueryParams;
    private Map<String, List<String>> headerParams;
    private PathTemplate template;

    public Info(String selector) {
      this.selector = selector;
      this.urlQueryParams = Maps.newHashMap();
      this.headerParams = Maps.newHashMap();
    }

    public PathTemplate getTemplate() {
      return template;
    }

    public void setTemplate(PathTemplate template) {
      this.template = template;
    }

    public void addUrlQueryParam(String name, String param) {
      List<String> l = urlQueryParams.get(name);
      if (l == null) {
        l = Lists.newArrayList();
        urlQueryParams.put(name, l);
      }
      l.add(param);
    }

    public List<String> urlQueryParam(String name) {
      List<String> l = urlQueryParams.get(name);
      if (l == null) {
        return Collections.emptyList();
      }
      return ImmutableList.copyOf(l);
    }

    public void addHeaderParam(String name, String param) {
      List<String> l = headerParams.get(name);
      if (l == null) {
        l = Lists.newArrayList();
        headerParams.put(name, l);
      }
      l.add(param);
    }

    public List<String> headerParam(String name) {
      List<String> l = headerParams.get(name);
      if (l == null) {
        return Collections.emptyList();
      }
      return ImmutableList.copyOf(l);
    }

    public boolean isAuth() {
      return auth;
    }

    public void setAuth(boolean auth) {
      this.auth = auth;
    }

    public boolean isAllowRegisteredCalls() {
      return allowRegisteredCalls;
    }

    public void setAllowRegisteredCalls(boolean allowRegisteredCalls) {
      this.allowRegisteredCalls = allowRegisteredCalls;
    }

    public String getSelector() {
      return selector;
    }

    public void setSelector(String selector) {
      this.selector = selector;
    }

    public String getBackendAddress() {
      return backendAddress;
    }

    public void setBackendAddress(String backendAddress) {
      this.backendAddress = backendAddress;
    }

    public String getBodyFieldPath() {
      return bodyFieldPath;
    }

    public void setBodyFieldPath(String bodyFieldPath) {
      this.bodyFieldPath = bodyFieldPath;
    }
  }
}
