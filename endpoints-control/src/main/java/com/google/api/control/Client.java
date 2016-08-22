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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.control.aggregator.CheckAggregationOptions;
import com.google.api.control.aggregator.CheckRequestAggregator;
import com.google.api.control.aggregator.ReportAggregationOptions;
import com.google.api.control.aggregator.ReportRequestAggregator;
import com.google.api.control.model.KnownLabels;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.api.services.servicecontrol.v1.Servicecontrol;
import com.google.api.services.servicecontrol.v1.ServicecontrolScopes;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Client is a package-level facade that encapsulates all service control functionality.
 */
public class Client {
  private static final Logger log = Logger.getLogger(Client.class.getName());
  private static final String CLIENT_APPLICATION_NAME = "Service Control Client";
  public static final int DO_NOT_LOG_STATS = -1;
  public static final SchedulerFactory DEFAULT_SCHEDULER_FACTORY = new SchedulerFactory() {
    @Override
    public Scheduler create(Ticker ticker) {
      return new Scheduler(ticker);
    }};
  private final CheckRequestAggregator checkAggregator;
  private final ReportRequestAggregator reportAggregator;
  private final Ticker ticker;
  private final ThreadFactory threads;
  private final SchedulerFactory schedulers;
  private final Servicecontrol transport;
  private boolean running;
  private boolean stopped;
  private Scheduler scheduler;
  private String serviceName;
  private Statistics statistics;
  private Thread schedulerThread;
  private int statsLogFrequency;

  public Client(String serviceName, CheckAggregationOptions checkOptions,
      ReportAggregationOptions reportOptions, Servicecontrol transport, ThreadFactory threads,
      SchedulerFactory schedulers, int statsLogFrequency, @Nullable Ticker ticker) {
    ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.checkAggregator = new CheckRequestAggregator(serviceName, checkOptions, null, ticker);
    this.reportAggregator = new ReportRequestAggregator(serviceName, reportOptions, null, ticker);
    this.serviceName = serviceName;
    this.ticker = ticker;
    this.transport = transport;
    this.threads = threads;
    this.schedulers = schedulers;
    this.scheduler  = null; // the scheduler is assigned when start is invoked
    this.schedulerThread = null;
    this.statsLogFrequency = statsLogFrequency;
    this.statistics = new Statistics();
  }

  /**
   * Starts processing.
   *
   * Calling this method
   *
   * starts the thread that regularly flushes all enabled caches. enables the other methods on the
   * instance to be called successfully
   *
   * I.e, even when the configuration disables aggregation, it is invalid to access the other
   * methods of an instance until ``start`` is called - Calls to other public methods will fail with
   * an {@code IllegalStateError}.
   */
  public synchronized void start() {
    if (running) {
      log.log(Level.INFO, String.format("%s is already started", this));
      return;
    }
    log.log(Level.INFO, String.format("starting %s", this));
    this.stopped = false;
    this.running = true;
    try {
      schedulerThread = threads.newThread(new Runnable() {
        @Override
        public void run() {
          scheduleFlushes();
        }
      });
      schedulerThread.start();
    } catch (RuntimeException e) {
      log.log(Level.WARNING, "no scheduler thread, schedule.run will be invoked by report(...)", e);
      schedulerThread = null;
      initializeFlushing();
    }
  }

  /**
   * Stops processing.
   *
   * Does not block waiting for processing to stop, but after this called, the scheduler thread if
   * it's active will come to a close
   */
  public void stop() {
    Preconditions.checkState(running, "Cannot stop if it's not running");

    synchronized (this) {
      log.log(Level.FINE, "flushing the report aggregator");
      for (ReportRequest req : reportAggregator.clear()) {
        try {
          transport.services().report(serviceName, req).execute();
        } catch (IOException e) {
          log.log(Level.SEVERE,
              String.format("direct send of a report request failed because of %s", e));
        }
      }
      this.stopped = true;  // the scheduler thread will set running to false
      if (isRunningSchedulerDirectly()) {
        resetIfStopped();
      }
      this.scheduler = null;
    }
  }

  /**
   * Process a check request.
   *
   * The {@code req} is first passed to the {@code CheckAggregator}. If there is a valid cached
   * response, that is returned, otherwise a response is obtained from the transport.
   *
   * @param req a {@link CheckRequest}
   * @return a {@link CheckResponse} or {@code null} if none was cached and there was a transport
   *         failure
   */
  public @Nullable CheckResponse check(CheckRequest req) {
    Preconditions.checkState(running, "Cannot check if it's not running");
    statistics.totalChecks.incrementAndGet();
    Stopwatch w = Stopwatch.createStarted(ticker);
    CheckResponse resp = checkAggregator.check(req);
    statistics.totalCheckCacheLookupTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
    if (resp != null) {
      statistics.checkHits.incrementAndGet();
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, String.format("using cached check response for %s: %s", req, resp));
      }
      return resp;
    }

    // Application code should not fail (or be blocked) because check request's do not succeed.
    // Instead they should fail open so here just simply log the error and return None to indicate
    // that no response was obtained.
    try {
      w.reset().start();
      resp = transport.services().check(serviceName, req).execute();
      statistics.totalCheckTransportTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
      checkAggregator.addResponse(req, resp);
      return resp;
    } catch (IOException e) {
      log.log(Level.SEVERE,
          String.format("direct send of a check request %s failed because of %s", req, e));
      return null;
    }
  }

  /**
   * Process a report request.
   *
   * The {@code req} is first passed to the {@code ReportAggregator}. It will either be aggregated
   * with prior requests or sent immediately
   *
   * @param req a {@link ReportRequest}
   */
  public void report(ReportRequest req) {
    Preconditions.checkState(running, "Cannot report if it's not running");
    statistics.totalReports.incrementAndGet();
    statistics.reportedOperations.addAndGet(req.getOperationsCount());
    Stopwatch w = Stopwatch.createStarted(ticker);
    boolean reported = reportAggregator.report(req);
    statistics.totalReportCacheUpdateTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
    if (!reported) {
      try {
        statistics.directReports.incrementAndGet();
        w.reset().start();
        transport.services().report(serviceName, req).execute();
        statistics.totalTransportedReportTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
      } catch (IOException e) {
        log.log(Level.SEVERE,
            String.format("direct send of a report request %s failed because of %s", req, e));
      }
    }

    if (isRunningSchedulerDirectly()) {
      try {
        scheduler.run(false /* don't block */);
      } catch (InterruptedException e) {
        log.log(Level.SEVERE,
            String.format("direct run of scheduler failed because of %s", e));
      }
    }
    logStatistics();
  }

  private void logStatistics() {
    if (statsLogFrequency < 1) {
      return;
    }
    if (statistics.totalReports.get() % statsLogFrequency == 0) {
      log.info(statistics.toString());
    }
  }

  private void scheduleFlushes() {
    try {
      initializeFlushing();
      this.scheduler.run(); // if caching is configured, this blocks until stop is called
      log.log(Level.INFO, String.format("scheduler %s has no further tasks and will exit", this));
      this.scheduler = null;
    } catch (InterruptedException e) {
      log.log(Level.SEVERE, String.format("scheduler %s was interrupted and exited", this), e);
      this.stopped = true;
    } catch (RuntimeException e) {
      log.log(Level.SEVERE, String.format("scheduler %s failed and exited", this), e);
      this.stopped = true;
    }
  }

  private boolean isRunningSchedulerDirectly() {
    return running && schedulerThread == null;
  }

  private synchronized void initializeFlushing() {
    log.info("creating a scheduler to control flushing");
    this.scheduler = schedulers.create(ticker);
    this.scheduler.setStatistics(statistics);
    log.info("scheduling the initial check and report");
    flushAndScheduleChecks();
    flushAndScheduleReports();
  }

  private synchronized boolean resetIfStopped() {
    if (!stopped) {
      return false;
    }

    // It's stopped to let's cleanup
    checkAggregator.clear();
    reportAggregator.clear();
    running = false;
    return true;
  }

  private void flushAndScheduleChecks() {
    if (resetIfStopped()) {
      log.log(Level.FINE, "did not schedule check flush: client is stopped");
      return;
    }
    int interval = checkAggregator.getFlushIntervalMillis();
    if (interval < 0) {
      log.log(Level.FINE, "did not schedule check flush: caching is disabled");
      return; // cache is disabled, so no flushing it
    }

    if (isRunningSchedulerDirectly()) {
      log.log(Level.FINE, "did not schedule check flush: no scheduler thread is running");
      return;
    }

    log.log(Level.FINE, "flushing the check aggregator");
    Stopwatch w = Stopwatch.createUnstarted(ticker);
    for (CheckRequest req : checkAggregator.flush()) {
      try {
        statistics.recachedChecks.incrementAndGet();
        w.reset().start();
        CheckResponse resp = transport.services().check(serviceName, req).execute();
        statistics.totalCheckTransportTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
        w.reset().start();
        checkAggregator.addResponse(req, resp);
        statistics.totalCheckCacheUpdateTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
      } catch (IOException e) {
        log.log(Level.SEVERE,
            String.format("direct send of a check request %s failed because of %s", req, e));
      }
    }
    scheduler.enter(new Runnable() {
      @Override
      public void run() {
        flushAndScheduleChecks(); // Do this again after the interval
      }
    }, interval, 0 /* high priority */);
  }

  private void flushAndScheduleReports() {
    if (resetIfStopped()) {
      log.log(Level.FINE, "did not schedule report flush: client is stopped");
      return;
    }
    int interval = reportAggregator.getFlushIntervalMillis();
    if (interval < 0) {
      log.log(Level.FINE, "did not schedule report flush: cache is disabled");
      return; // cache is disabled, so no flushing it
    }
    ReportRequest[] flushed = reportAggregator.flush();
    if (log.isLoggable(Level.FINE)) {
      log.log(Level.FINE,
          String.format("flushing %d reports from the report aggregator", flushed.length));
    }
    statistics.flushedReports.addAndGet(flushed.length);
    Stopwatch w = Stopwatch.createUnstarted(ticker);
    for (ReportRequest req : flushed) {
      try {
        statistics.flushedOperations.addAndGet(req.getOperationsCount());
        w.reset().start();
        transport.services().report(serviceName, req).execute();
        statistics.totalTransportedReportTimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
      } catch (IOException e) {
        log.log(Level.SEVERE,
            String.format("direct send of a report request failed because of %s", e));
      }
    }
    scheduler.enter(new Runnable() {
      @Override
      public void run() {
        flushAndScheduleReports(); // Do this again after the interval
      }
    }, interval, 1 /* not so high priority */);
  }

  /**
   * Builder provide structure to the construction of a {@link Client}
   */
  public static class Builder {
    private int statsLogFrequency;
    private Ticker ticker;
    private HttpTransport transport;
    private ThreadFactory factory;
    private String serviceName;
    private CheckAggregationOptions checkOptions;
    private ReportAggregationOptions reportOptions;
    private SchedulerFactory schedulerFactory = DEFAULT_SCHEDULER_FACTORY;

    public Builder(String name) {
      this.serviceName = name;
    }

    public Builder setTicker(Ticker ticker) {
      this.ticker = ticker;
      return this;
    }

    public Builder setStatsLogFrequency(int frequency) {
      this.statsLogFrequency = frequency;
      return this;
    }

    public Builder setCheckOptions(CheckAggregationOptions options) {
      this.checkOptions = options;
      return this;
    }

    public Builder setReportOptions(ReportAggregationOptions options) {
      this.reportOptions = options;
      return this;
    }

    public Builder setHttpTransport(HttpTransport transport) {
      this.transport = transport;
      return this;
    }

    public Builder setFactory(ThreadFactory factory) {
      this.factory = factory;
      return this;
    }

    public Builder setSchedulerFactory(SchedulerFactory f) {
      this.schedulerFactory = f;
      return this;
    }

    public Client build() throws GeneralSecurityException, IOException {
      HttpTransport h = this.transport;
      if (h == null) {
        h = GoogleNetHttpTransport.newTrustedTransport();
      }
      GoogleCredential c = GoogleCredential.getApplicationDefault(transport, new JacksonFactory());
      if (c.createScopedRequired()) {
        c = c.createScoped(ServicecontrolScopes.all());
      }
      ThreadFactory f = this.factory;
      if (f == null) {
        f = new ThreadFactoryBuilder().build();
      }
      CheckAggregationOptions o = this.checkOptions;
      if (o == null) {
        o = new CheckAggregationOptions();
      }
      ReportAggregationOptions r = this.reportOptions;
      if (r == null) {
        r = new ReportAggregationOptions();
      }
      final GoogleCredential nestedInitializer = c;
      HttpRequestInitializer addUserAgent = new HttpRequestInitializer() {
        @Override
        public void initialize(HttpRequest request) throws IOException {
          HttpHeaders hdr = new HttpHeaders().setUserAgent(KnownLabels.USER_AGENT);
          request.setHeaders(hdr);
          nestedInitializer.initialize(request);
        }
      };
      return new Client(serviceName, o, r,
          new Servicecontrol.Builder(h, c)
              .setHttpRequestInitializer(addUserAgent)
          .setApplicationName(CLIENT_APPLICATION_NAME)
              .build(),
          f, schedulerFactory, statsLogFrequency, ticker);
    }
  }

  /**
   * Statistics contains information about the performance of a {@code Client}.
   */
  static class Statistics {
    // counts
    AtomicLong checkHits = new AtomicLong();

    AtomicLong directReports = new AtomicLong();
    AtomicLong flushedOperations = new AtomicLong();
    AtomicLong flushedReports = new AtomicLong();
    AtomicLong recachedChecks = new AtomicLong();
    AtomicLong reportedOperations = new AtomicLong();
    AtomicLong totalChecks = new AtomicLong();
    AtomicLong totalReports = new AtomicLong();
    AtomicLong totalSchedulerRuns = new AtomicLong();
    AtomicLong totalSchedulerSkips = new AtomicLong();

    // latencies
    AtomicLong totalCheckCacheLookupTimeMillis = new AtomicLong();
    AtomicLong totalCheckCacheUpdateTimeMillis = new AtomicLong();
    AtomicLong totalCheckTransportTimeMillis = new AtomicLong();
    AtomicLong totalTransportedReportTimeMillis = new AtomicLong();
    AtomicLong totalReportCacheUpdateTimeMillis = new AtomicLong();
    AtomicLong totalSchedulerSkiptimeMillis = new AtomicLong();
    AtomicLong totalSchedulerRuntimeMillis = new AtomicLong();

    public double checkHitsPercent() {
      return divide(100 * checkHits.get(), totalChecks.get());
    }

    public double flushedReportsPercent() {
      return divide(100 * flushedReports.get(), totalReports.get());
    }

    public long directChecks() {
      return totalChecks.get() - checkHits.get();
    }

    public long totalChecksTransported() {
      return directChecks() + recachedChecks.get();
    }

    public long totalReportsTransported() {
      return directReports.get() + flushedReports.get();
    }

    public double meanTransportedReportTimeMillis() {
      return divide(totalTransportedReportTimeMillis.get(), totalReportsTransported());
    }

    public double meanReportCacheUpdateTimeMillis() {
      long count = totalReports.get() - directReports.get();
      return divide(totalReportCacheUpdateTimeMillis.get(), count);
    }

    public double meanTransportedCheckTimeMillis() {
      return divide(totalCheckTransportTimeMillis.get(), totalChecksTransported());
    }

    public double meanCheckCacheLookupTimeMillis() {
      return divide(totalCheckCacheLookupTimeMillis, totalChecks);
    }

    public double meanCheckCacheUpdateTimeMillis() {
      return divide(totalCheckCacheUpdateTimeMillis.get(), totalChecksTransported());
    }

    private static double divide(AtomicLong dividend, AtomicLong divisor) {
      return divide(dividend.get(), divisor.get());
    }

    private static double divide(long dividend, long divisor) {
      if (divisor == 0) {
        return 0;
      }
      return 1.0 * dividend / divisor;
    }

    @Override
    public String toString() {
      final String nl = "\n  "; // Use a consistent space to make the output valid YAML
      return "statistics:"
          + nl + "totalChecks:" + totalChecks.get()
          + nl + "checkHits:" + checkHits.get()
          + nl + "checkHitsPercent:" + checkHitsPercent()
          + nl + "recachedChecks:" + recachedChecks.get()
          + nl + "totalChecksTransported:" + totalChecksTransported()
          + nl + "totalTransportedCheckTimeMillis:" + totalCheckTransportTimeMillis.get()
          + nl + "meanTransportedCheckTimeMillis:" + meanTransportedCheckTimeMillis()
          + nl + "totalCheckCacheLookupTimeMillis:" + totalCheckCacheLookupTimeMillis.get()
          + nl + "meanCheckCacheLookupTimeMillis:" + meanCheckCacheLookupTimeMillis()
          + nl + "totalCheckCacheUpdateTimeMillis:" + totalCheckCacheUpdateTimeMillis.get()
          + nl + "meanCheckCacheUpdateTimeMillis:" + meanCheckCacheUpdateTimeMillis()
          + nl + "totalReports:" + totalReports.get()
          + nl + "flushedReports:" + flushedReports.get()
          + nl + "directReports:" + directReports.get()
          + nl + "flushedReportsPercent:" + flushedReportsPercent()
          + nl + "totalReportsTransported:" + totalReportsTransported()
          + nl + "totalTransportedReportTimeMillis:" + totalTransportedReportTimeMillis.get()
          + nl + "meanTransportedReportTimeMillis:" + meanTransportedReportTimeMillis()
          + nl + "totalReportCacheUpdateTimeMillis:" + totalReportCacheUpdateTimeMillis.get()
          + nl + "meanReportCacheUpdateTimeMillis:" + meanReportCacheUpdateTimeMillis()
          + nl + "flushedOperations:" + flushedOperations.get()
          + nl + "reportedOperations:" + reportedOperations.get()
          + nl + "totalSchedulerRuns:" + totalSchedulerRuns.get()
          + nl + "totalSchedulerRuntimeMillis:" + totalSchedulerRuntimeMillis.get()
          + nl + "meanSchedulerRuntimeMillis:" + divide(totalSchedulerRuntimeMillis, totalSchedulerRuns)
          + nl + "totalSchedulerSkips:" + totalSchedulerSkips.get()
          + nl + "totalSchedulerSkiptimeMillis:" + totalSchedulerSkiptimeMillis.get()
          + nl + "meanSchedulerSkiptimeMillis:" + divide(totalSchedulerSkiptimeMillis, totalSchedulerSkips);
    }
  }

  /**
   * SchedulerFactory defines a method for creating {@link Scheduler} instances
   */
  interface SchedulerFactory {
    /**
     * @param ticker obtains time updates from a time source.
     * @return a {@ link Scheduler}
     */
    Scheduler create(Ticker ticker);
  }

  /**
   * Scheduler uses a {@code PriorityQueue} to maintain a series of {@link Runnable}
   */
  static class Scheduler {
    private PriorityQueue<ScheduledEvent> queue;
    private Ticker ticker;
    private Statistics statistics;

    Scheduler(Ticker ticker) {
      this.queue = Queues.newPriorityQueue();
      this.ticker = ticker;
    }

    /**
     * @param r a {@code Runnable} to run after {@code deltaNanos}
     * @param deltaMillis the time in the future to run {@code r}
     * @param priority the priority at which to give running {@code r}
     */
    public void enter(Runnable r, long deltaMillis, int priority) {
      long later = TimeUnit.MILLISECONDS.toNanos(deltaMillis) + ticker.read();
      ScheduledEvent event = new ScheduledEvent(r, later, priority);
      synchronized (this) {
        queue.add(event);
      }
    }

    public void run(boolean block) throws InterruptedException {
      while (!this.queue.isEmpty()) {
        boolean delay = true;
        ScheduledEvent next = null;
        long gap = 0;
        synchronized (this) {
          next = queue.peek();
          long now = ticker.read();
          gap = next.getTickerTime() - now;
          if (gap > 0) {
            delay = true;
            next = null;
          } else {
            next = queue.remove();
            delay = false;
          }
        }
        if (delay) {
          long gapMillis = TimeUnit.NANOSECONDS.toMillis(gap);
          if (statistics != null) {
            statistics.totalSchedulerSkips.incrementAndGet();
            statistics.totalSchedulerSkiptimeMillis.addAndGet(gapMillis);
          }
          if (!block) {
            if (log.isLoggable(Level.FINE)) {
              log.log(Level.FINE,
                  String.format("Scheduler on %s was not blocking, next event is in %d",
                      Thread.currentThread(), gapMillis));
            }
            return;
          }
          if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, String.format("Scheduler on %s will sleep for %d millis",
                Thread.currentThread(), gapMillis));
          }
          Thread.sleep(gapMillis);
        } else {
          if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE,
                String.format("Scheduler on %s will run an event", Thread.currentThread()));
          }
          Stopwatch w = Stopwatch.createStarted(ticker);
          next.getScheduledAction().run();
          if (statistics != null) {
            statistics.totalSchedulerRuns.incrementAndGet();
            statistics.totalSchedulerRuntimeMillis.addAndGet(w.elapsed(TimeUnit.MILLISECONDS));
          }
        }
      }
    }

    public void run() throws InterruptedException {
      run(true);
    }

    public void setStatistics(Statistics statistics) {
      this.statistics = statistics;
    }
  }

  /**
   * ScheduledEvent holds the data for scheduling an event to be run the scheduling thread.
   *
   * It is {@link Comparable}, with the compare function coded so that the low values (i.e earlier
   * times) take precedence. Lower priority is also takes precedence.
   */
  private static class ScheduledEvent implements Comparable<ScheduledEvent> {
    private Runnable scheduledAction;
    private long tickerTime;
    private int priority;

    public ScheduledEvent(Runnable scheduledAction, long tickerTime, int priority) {
      this.scheduledAction = scheduledAction;
      this.tickerTime = tickerTime;
      this.priority = priority;
    }

    public Runnable getScheduledAction() {
      return scheduledAction;
    }

    public long getTickerTime() {
      return tickerTime;
    }

    @Override
    public int compareTo(ScheduledEvent o) {
      int timeCompare = Long.compare(this.tickerTime, o.tickerTime);
      if (timeCompare != 0) {
        return timeCompare;
      }
      return Long.compare(this.priority, o.priority);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + priority;
      result = prime * result + (int) (tickerTime ^ (tickerTime >>> 32));
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ScheduledEvent other = (ScheduledEvent) obj;
      if (tickerTime != other.tickerTime) {
        return false;
      }
      if (priority != other.priority) {
        return false;
      }
      return true;
    }
  }
}
