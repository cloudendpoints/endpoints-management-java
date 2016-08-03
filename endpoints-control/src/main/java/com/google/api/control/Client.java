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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

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
import com.google.common.base.Ticker;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Client is a package-level facade that encapsulates all service control functionality.
 */
public class Client {
  private static final Logger log = Logger.getLogger(Client.class.getName());
  private static final String CLIENT_APPLICATION_NAME = "Service Control Client";
  private final CheckRequestAggregator checkAggregator;
  private final ReportRequestAggregator reportAggregator;
  private final Ticker ticker;
  private boolean running;
  private boolean stopped;
  private Servicecontrol transport;
  private ThreadFactory threads;
  private Scheduler scheduler;
  private String serviceName;

  public Client(String serviceName, CheckAggregationOptions checkOptions,
      ReportAggregationOptions reportOptions, Servicecontrol transport, ThreadFactory threads,
      @Nullable Ticker ticker) {
    ticker = ticker == null ? Ticker.systemTicker() : ticker;
    this.checkAggregator = new CheckRequestAggregator(serviceName, checkOptions, null, ticker);
    this.reportAggregator = new ReportRequestAggregator(serviceName, reportOptions, null, ticker);
    this.serviceName = serviceName;
    this.ticker = ticker;
    this.transport = transport;
    this.threads = threads;
    this.scheduler = null; // the scheduler is assigned when start is invoked
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
    Thread t = threads.newThread(new Runnable() {
      @Override
      public void run() {
        scheduleFlushes();
      }
    });
    t.start();
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
    CheckResponse resp = checkAggregator.check(req);
    if (resp != null) {
      return resp;
    }

    // Application code should not fail (or be blocked) because check request's do not succeed.
    // Instead they should fail open so here just simply log the error and return None to indicate
    // that no response was obtained.
    try {
      resp = transport.services().check(serviceName, req).execute();
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
    if (!reportAggregator.report(req)) {
      try {
        transport.services().report(serviceName, req).execute();
      } catch (IOException e) {
        log.log(Level.SEVERE,
            String.format("direct send of a report request %s failed because of %s", req, e));
      }
    }
  }

  private void scheduleFlushes() {
    try {
      this.scheduler = new Scheduler(ticker);
      flushAndScheduleChecks();
      flushAndScheduleReports();
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
      return;
    }
    int interval = checkAggregator.getFlushIntervalMillis();
    if (interval < 0) {
      return; // cache is disabled, so no flushing it
    }
    log.log(Level.FINE, "flushing the check aggregator");
    for (CheckRequest req : checkAggregator.flush()) {
      try {
        transport.services().check(serviceName, req).execute();
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
      return;
    }
    int interval = reportAggregator.getFlushIntervalMillis();
    if (interval < 0) {
      return; // cache is disabled, so no flushing it
    }
    log.log(Level.FINE, "flushing the report aggregator");
    for (ReportRequest req : reportAggregator.flush()) {
      try {
        transport.services().report(serviceName, req).execute();
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
    private Ticker ticker;
    private HttpTransport transport;
    private ThreadFactory factory;
    private String serviceName;
    private CheckAggregationOptions checkOptions;
    private ReportAggregationOptions reportOptions;

    public Builder(String serviceName) {
      this.serviceName = serviceName;
    }

    public Builder setTicker(Ticker ticker) {
      this.ticker = ticker;
      return this;
    }

    public Builder setCheckOptions(CheckAggregationOptions checkOptions) {
      this.checkOptions = checkOptions;
      return this;
    }

    public Builder setReportOptions(ReportAggregationOptions reportOptions) {
      this.reportOptions = reportOptions;
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
          HttpHeaders hdr = new HttpHeaders().setUserAgent(
              KnownLabels.USER_AGENT);
          request.setHeaders(hdr);
          nestedInitializer.initialize(request);
        }
      };
      return new Client(serviceName, o, r,
          new Servicecontrol.Builder(h, c)
              .setHttpRequestInitializer(addUserAgent)
          .setApplicationName(CLIENT_APPLICATION_NAME)
          .build(), f, ticker);
    }
  }

  /**
   * Scheduler uses a {@code PriorityQueue} to maintain a series of {@link Runnable}
   */
  private static class Scheduler {
    private static final int NANOS_PER_MILLIS = 1000000;
    private PriorityQueue<ScheduledEvent> queue;
    private Ticker ticker;

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
      long later = (deltaMillis * NANOS_PER_MILLIS) + ticker.read();
      ScheduledEvent event = new ScheduledEvent(r, later, priority);
      synchronized (this) {
        queue.add(event);
      }
    }

    public void run() throws InterruptedException {
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
          long gapMillis = gap / NANOS_PER_MILLIS;
          log.log(Level.FINE, String.format("Scheduler on %s will sleep for %d millis", Thread.currentThread(), gapMillis));
          Thread.sleep(gapMillis);
        } else {
          log.log(Level.FINE, String.format("Scheduler on %s will run an event", Thread.currentThread()));
          next.getScheduledAction().run();
        }
      }
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
      if (priority != other.priority) {
        return false;
      }
      if (tickerTime != other.tickerTime) {
        return false;
      }
      return true;
    }
  }
}
