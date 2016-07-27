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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.api.LabelDescriptor;
import com.google.api.LogDescriptor;
import com.google.api.Logging.LoggingDestination;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResourceDescriptor;
import com.google.api.Monitoring;
import com.google.api.Monitoring.MonitoringDestination;
import com.google.api.Service;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import autovalue.shaded.com.google.common.common.collect.Sets;

/**
 * ReportingRule determines how to fill a report request.
 */
public class ReportingRule {
  private static final Logger log = Logger.getLogger(ReportingRule.class.getName());

  /**
   * MetricSupporter defines a method that determines is a metric is supported
   */
  public static interface MetricTest {
    boolean isSupported(MetricDescriptor m);
  }

  /**
   * LabelSupporter defines a method that determines if a metric is supported
   */
  public static interface LabelTest {
    boolean isSupported(LabelDescriptor l);
  }

  private static final MetricTest KNOWN_METRICS = new MetricTest() {
    @Override
    public boolean isSupported(MetricDescriptor m) {
      return KnownMetrics.isSupported(m);
    }
  };

  private static final LabelTest KNOWN_LABELS = new LabelTest() {
    @Override
    public boolean isSupported(LabelDescriptor l) {
      return KnownLabels.isSupported(l);
    }
  };

  /**
   * 'Constructor' that uses a {@code Service}.
   *
   * @param s the {@code Service} whose metric, labels and logs configurations are used to
   *        configure the {@code ReportingRule}
   * @return {@code ReportingRule}
   */
  public static ReportingRule fromService(Service s) {
    return fromService(s, KNOWN_METRICS, KNOWN_LABELS);
  }

  /**
   * 'Constructor' that uses a {@code Service}.
   *
   * @param s the {@code Service} whose metric, labels and logs configurations are used to
   *        configure the {@code ReportingRule}
   *
   * @param checkMetrics used to determine which {@code MetricDescriptors} are allowed
   * @param checkLabels used to determine which {@code LabelDescriptors} are allowed
   *
   * @return {@code ReportingRule}
   */
  public static ReportingRule fromService(Service s, ReportingRule.MetricTest checkMetrics,
      ReportingRule.LabelTest checkLabels) {
    List<MonitoredResourceDescriptor> resourceDescs = s.getMonitoredResourcesList();
    Map<String, LabelDescriptor> labels = Maps.newHashMap();
    Set<String> logs = Sets.newHashSet();
    if (s.hasLogging()) {
      List<LoggingDestination> producers = s.getLogging().getProducerDestinationsList();
      logs = addLoggingDestinations(producers, resourceDescs, s.getLogsList(), labels,
          checkLabels);
    }
    Set<String> metrics = Sets.newHashSet();
    if (s.hasMonitoring()) {
      Monitoring monitoring = s.getMonitoring();
      addMonitoringDestinations(monitoring.getConsumerDestinationsList(), resourceDescs,
          s.getMetricsList(), metrics, checkMetrics, labels, checkLabels);
      addMonitoringDestinations(monitoring.getProducerDestinationsList(), resourceDescs,
          s.getMetricsList(), metrics, checkMetrics, labels, checkLabels);
    }
    return ReportingRule.fromKnownInputs(logs.toArray(new String[logs.size()]),
        Lists.newArrayList(metrics.iterator()), Lists.newArrayList(labels.keySet().iterator()));
  }

  /**
   * 'Constructor' that uses names of known {@code KnownMetrics} and {@code KnownLabels}.
   *
   * Names that don't correspond to actual instances are ignored, as are instances where there is
   * not yet an update function that will modify a {@code ReportRequest}
   *
   *
   * @param logs the {@code logs} for which entries will be added {@code ReportRequests}
   * @param metricNames the names of the {@code KnownMetrics} to use
   * @param labelNames the names of the {@code KnownLabels} to use
   * @return {@code ReportingRule}
   */
  public static ReportingRule fromKnownInputs(@Nullable String[] logs,
      @Nullable List<String> metricNames, @Nullable List<String> labelNames) {
    KnownMetrics[] metrics = null;
    if (metricNames != null) {
      ArrayList<KnownMetrics> l = Lists.newArrayList();
      for (KnownMetrics m : KnownMetrics.values()) {
        if (m.getUpdater() == null || !metricNames.contains(m.getName())) {
          continue;
        }
        l.add(m);
        metrics = l.toArray(new KnownMetrics[l.size()]);
      }
    }
    KnownLabels[] labels = null;
    if (labelNames != null) {
      ArrayList<KnownLabels> knownLabels = Lists.newArrayList();
      for (KnownLabels k : KnownLabels.values()) {
        if (k.getUpdater() == null || !labelNames.contains(k.getName())) {
          continue;
        }
        knownLabels.add(k);
        metrics = knownLabels.toArray(new KnownMetrics[knownLabels.size()]);
      }
    }
    return new ReportingRule(logs, metrics, labels);
  }

  /**
   * Constructor that uses {@code KnownMetrics} and {@code KnownLabels} instance directly
   *
   * @param logs the {@code logs} for which entries will be added {@code ReportRequests}
   * @param metrics the {@code KnownMetrics} used to add metrics to {@code ReportRequests}
   * @param labels the {@code KnownLabels} used to add labels to {@code ReportRequests}
   */
  public ReportingRule(@Nullable String[] logs, @Nullable KnownMetrics[] metrics,
      @Nullable KnownLabels[] labels) {
    if (logs == null) {
      this.logs = new String[] {};
    } else {
      this.logs = logs;
    }
    if (metrics == null) {
      this.metrics = new KnownMetrics[] {};
    } else {
      this.metrics = metrics;
    }
    if (labels == null) {
      this.labels = new KnownLabels[] {};
    } else {
      this.labels = labels;
    }
  }

  /**
   * @return the {@code logs} for which entries will be added {@code ReportRequests}
   */
  public String[] getLogs() {
    return logs;
  }

  /**
   * @return the {@code KnownMetrics} used to add metrics to {@code ReportRequests}
   */
  public KnownMetrics[] getMetrics() {
    return metrics;
  }

  /**
   * @return the {@code KnownLabels} used to add labels to {@code ReportRequests}
   */
  public KnownLabels[] getLabels() {
    return labels;
  }

  private final String[] logs;
  private final KnownMetrics[] metrics;
  private final KnownLabels[] labels;

  private static Set<String> addLoggingDestinations(List<LoggingDestination> destinations,
      List<MonitoredResourceDescriptor> resourceDescs, List<LogDescriptor> logDescs,
      Map<String, LabelDescriptor> labels, ReportingRule.LabelTest checkLabels) {
    Set<String> logs = Sets.newHashSet();
    for (LoggingDestination d : destinations) {
      if (!addLabelsForAMonitoredResource(resourceDescs, d.getMonitoredResource(), labels,
          checkLabels)) {
        continue;
      }
      for (String name : d.getLogsList()) {
        if (!addLabelsForALog(logDescs, name, labels, checkLabels)) {
          logs.add(name);
        }
      }
    }
    return logs;
  }

  private static void addMonitoringDestinations(List<MonitoringDestination> destinations,
      List<MonitoredResourceDescriptor> resourceDescs, List<MetricDescriptor> metricDests,
      Set<String> metrics, ReportingRule.MetricTest checkMetric, Map<String, LabelDescriptor> labels,
      ReportingRule.LabelTest checkLabel) {
    for (MonitoringDestination d : destinations) {
      if (!addLabelsForAMonitoredResource(resourceDescs, d.getMonitoredResource(), labels,
          checkLabel)) {
        continue;
      }
      for (String metric : d.getMetricsList()) {
        MetricDescriptor metricDest = findMetricDescriptor(metricDests, metric, checkMetric);
        if (metricDest != null
            && !addLabelsFromDescriptors(metricDest.getLabelsList(), labels, checkLabel)) {
          continue; // skip unrecognized or bad metric, or it has bad labels
        }
        metrics.add(metric);
      }
    }
  }

  private static boolean addLabelsFromDescriptors(List<LabelDescriptor> labelDescs,
      Map<String, LabelDescriptor> labels, ReportingRule.LabelTest check) {
    for (LabelDescriptor d : labelDescs) {
      LabelDescriptor existing = labels.get(d.getKey());
      if (existing != null && !existing.getValueType().equals(d.getValueType())) {
        return false;
      }
    }
    for (LabelDescriptor d : labelDescs) {
      if (check.isSupported(d)) {
        log.log(Level.WARNING,
            String.format("halted label scan: conflicting label in %s", d.getKey()));
        labels.put(d.getKey(), d);
      }
    }
    return true;
  }

  private static boolean addLabelsForALog(List<LogDescriptor> logDescs, String name,
      Map<String, LabelDescriptor> labels, ReportingRule.LabelTest check) {
    for (LogDescriptor d : logDescs) {
      if (d.getName().equals(name)) {
        return addLabelsFromDescriptors(d.getLabelsList(), labels, check);
      }
    }
    log.log(Level.WARNING, String.format("bad log label scan: log %s was not found", name));
    return false;
  }

  private static boolean addLabelsForAMonitoredResource(
      List<MonitoredResourceDescriptor> resourceDescs, String name,
      Map<String, LabelDescriptor> labels, ReportingRule.LabelTest check) {
    for (MonitoredResourceDescriptor d : resourceDescs) {
      if (d.getName().equals(name)) {
        return addLabelsFromDescriptors(d.getLabelsList(), labels, check);
      }
    }
    log.log(Level.WARNING,
        String.format("bad monitored resource label scan: resource %s was not found", name));
    return false;
  }

  private static MetricDescriptor findMetricDescriptor(List<MetricDescriptor> metricDests,
      String metric, ReportingRule.MetricTest check) {
    for (MetricDescriptor d : metricDests) {
      if (!d.getName().equals(metric)) {
        continue;
      }
      if (check.isSupported(d)) {
        return d;
      }
    }
    return null;
  }
}
