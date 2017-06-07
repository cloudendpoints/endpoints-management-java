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

import com.google.api.AuthRequirement;
import com.google.api.AuthenticationRule;
import com.google.api.HttpRule;
import com.google.api.MetricRule;
import com.google.api.Service;
import com.google.api.SystemParameter;
import com.google.api.SystemParameterRule;
import com.google.api.UsageRule;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * MethodRegistry provides registry of the API methods defined by a Service.
 */
public class MethodRegistry {
  private static final Logger log = Logger.getLogger(MethodRegistry.class.getName());
  private static final String OPTIONS_VERB = "OPTIONS";
  private final Service theService;
  private final Map<String, AuthInfo> authInfos;
  private final Map<String, QuotaInfo> quotaInfos;
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
    authInfos = extractAuth(s);
    quotaInfos = extractQuota(s);

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
          String.format("no information about any urls for HTTP method %s when checking %s", httpMethod, url));
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
        infosByHttpMethod.put(httpMethod.toLowerCase(), infos);
      }
      infos.add(theMethod);
      log.log(Level.FINE,
          String.format("registered template %s under method %s", t, httpMethod));
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
    newInfo.setAllowUnregisteredCalls(true);
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
        info.setAllowUnregisteredCalls(r.getAllowUnregisteredCalls());
      }
    }
  }

  private Info getOrCreateInfo(String selector) {
    Info i = extractedMethods.get(selector);
    if (i != null) {
      return i;
    }

    i = new Info(selector, this.authInfos.get(selector), this.quotaInfos.get(selector));
    extractedMethods.put(selector, i);
    return i;
  }

  private static String httpMethodFrom(HttpRule r) {
    switch (r.getPatternCase()) {
      case CUSTOM:
        return r.getCustom().getKind().toLowerCase();
      case DELETE:
      case GET:
      case PATCH:
      case POST:
      case PUT:
        return r.getPatternCase().toString().toLowerCase();
      default:
        return null;
    }
  }

  private static String urlFrom(HttpRule r) {
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

  private static Map<String, AuthInfo> extractAuth(Service service) {
    if (!service.hasAuthentication()) {
      return ImmutableMap.<String, AuthInfo>of();
    }
    ImmutableMap.Builder<String, AuthInfo> authInfoBuilder = ImmutableMap.builder();
    for (AuthenticationRule authRule : service.getAuthentication().getRulesList()) {
      ImmutableMap.Builder<String, Set<String>> providerToAudienceBuilder = ImmutableMap.builder();
      for (AuthRequirement requirement : authRule.getRequirementsList()) {
        providerToAudienceBuilder.put(
            requirement.getProviderId(),
            ImmutableSet.copyOf(requirement.getAudiences().split(",")));
      }
      AuthInfo authInfo = new AuthInfo(providerToAudienceBuilder.build());
      authInfoBuilder.put(authRule.getSelector(), authInfo);
    }
    return authInfoBuilder.build();
  }

  private static Map<String, QuotaInfo> extractQuota(Service service) {
    if (!service.hasQuota()) {
      return ImmutableMap.<String, QuotaInfo>of();
    }
    ImmutableMap.Builder<String, QuotaInfo> quotaInfoBuilder = ImmutableMap.builder();
    for (MetricRule metricRule : service.getQuota().getMetricRulesList()) {
      quotaInfoBuilder.put(
          metricRule.getSelector(), QuotaInfo.create(metricRule.getMetricCostsMap()));
    }
    return quotaInfoBuilder.build();
  }

  /**
   * Consolidates information about methods defined in a Service
   */
  public static class Info {
    private static final String API_KEY_NAME = "api_key";

    private final Optional<AuthInfo> authInfo;
    private final QuotaInfo quotaInfo;

    private boolean allowUnregisteredCalls;
    private String selector;
    private String backendAddress;
    private String bodyFieldPath;
    private Map<String, List<String>> urlQueryParams;
    private Map<String, List<String>> headerParams;
    private PathTemplate template;

    public Info(String selector, @CheckForNull AuthInfo authInfo,
        @CheckForNull QuotaInfo quotaInfo) {
      this.selector = selector;
      this.authInfo = Optional.<AuthInfo>fromNullable(authInfo);
      this.quotaInfo = quotaInfo != null ? quotaInfo : QuotaInfo.DEFAULT;

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

    public List<String> apiKeyUrlQueryParam() {
      return urlQueryParam(API_KEY_NAME);
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

    public List<String> apiKeyHeaderParam() {
      return headerParam(API_KEY_NAME);
    }

    public Optional<AuthInfo> getAuthInfo() {
      return this.authInfo;
    }

    public QuotaInfo getQuotaInfo() {
      return quotaInfo;
    }

    public boolean shouldAllowUnregisteredCalls() {
      return allowUnregisteredCalls;
    }

    public void setAllowUnregisteredCalls(boolean allowRegisteredCalls) {
      this.allowUnregisteredCalls = allowRegisteredCalls;
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

  /**
   * Consolidates authentication information about methods defined in a Service
   */
  public static final class AuthInfo {
    private final Map<String, Set<String>> providerIdsToAudiences;

    public AuthInfo(Map<String, Set<String>> providerIdsToAudiences) {
      Preconditions.checkNotNull(providerIdsToAudiences);
      this.providerIdsToAudiences = providerIdsToAudiences;
    }

    public boolean isProviderIdAllowed(String providerid) {
      Preconditions.checkNotNull(providerid);
      return this.providerIdsToAudiences.containsKey(providerid);
    }

    public Set<String> getAudiencesForProvider(String providerId) {
      Preconditions.checkNotNull(providerId);

      if (this.providerIdsToAudiences.containsKey(providerId)) {
        return this.providerIdsToAudiences.get(providerId);
      }
      return ImmutableSet.<String>of();
    }
  }

  @AutoValue
  public abstract static class QuotaInfo {
    public static final QuotaInfo DEFAULT = QuotaInfo.create(ImmutableMap.<String, Long>of());

    public abstract Map<String, Long> getMetricCosts();

    public static QuotaInfo create(Map<String, Long> metricCosts) {
      return new AutoValue_MethodRegistry_QuotaInfo(metricCosts);
    }
  }
}
