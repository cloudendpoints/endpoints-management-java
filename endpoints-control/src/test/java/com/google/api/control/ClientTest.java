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

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.control.aggregator.CheckAggregationOptions;
import com.google.api.control.aggregator.FakeTicker;
import com.google.api.control.aggregator.ReportAggregationOptions;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.CheckResponse;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.api.servicecontrol.v1.ReportRequest.Builder;
import com.google.api.servicecontrol.v1.ReportResponse;
import com.google.api.services.servicecontrol.v1.Servicecontrol;
import com.google.api.services.servicecontrol.v1.Servicecontrol.Services;
import com.google.api.services.servicecontrol.v1.Servicecontrol.Services.Check;
import com.google.api.services.servicecontrol.v1.Servicecontrol.Services.Report;
import com.google.common.base.Ticker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@code Client}.
 */
@RunWith(JUnit4.class)
public class ClientTest {
  private static final String TEST_OPERATION_NAME = "aTestOperation";
  private static final String TEST_CONSUMER_ID = "testConsumerId";
  private static final String TEST_SERVICE_NAME = "testServiceName";
  private CheckAggregationOptions checkOptions;
  private ReportAggregationOptions reportOptions;
  private FakeTicker testTicker;
  private Servicecontrol transport;
  private ThreadFactory threads;
  private Client client;
  private Thread aThread;
  private Services services;
  private Check checkStub;
  private Report reportStub;
  private Client.SchedulerFactory schedulers;

  @Before
  public void setUp() throws IOException {
    testTicker = new FakeTicker();
    checkStub = mock(Check.class);
    reportStub = mock(Report.class);
    services = mock(Services.class);
    transport = mock(Servicecontrol.class);
    threads = mock(ThreadFactory.class);
    aThread = mock(Thread.class);
    schedulers = mock(Client.SchedulerFactory.class);
    checkOptions = new CheckAggregationOptions();
    reportOptions = new ReportAggregationOptions();

    client =
        new Client(TEST_SERVICE_NAME, checkOptions, reportOptions, transport, threads, schedulers,
            1 /* ensure stats dumping code is touched */, testTicker);
    when(threads.newThread(any(Runnable.class))).thenReturn(aThread);
    when(schedulers.create(any(Ticker.class))).thenReturn(new Client.Scheduler(testTicker));
    when(transport.services()).thenReturn(services);
    when(services.check(eq(TEST_SERVICE_NAME), any(CheckRequest.class))).thenReturn(checkStub);
    when(services.report(eq(TEST_SERVICE_NAME), any(ReportRequest.class))).thenReturn(reportStub);
    when(checkStub.execute()).thenReturn(CheckResponse.newBuilder().build());
    when(reportStub.execute()).thenReturn(ReportResponse.newBuilder().build());
  }

  @Test
  public void startShouldCreateAThread() {
    client.start();
    verify(threads, times(1)).newThread(any(Runnable.class));
    verify(aThread, times(1)).start();
  }

  @Test
  public void startIsIgnoredIfAlreadyStarted() {
    client.start();
    client.start();
    verify(threads, times(1)).newThread(any(Runnable.class));
    verify(aThread, times(1)).start();
  }

  @Test
  public void checkFailsIfNotStarted() {
    try {
      client.check(newTestCheck());
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void checkInvokesTheTransportIfRequestIsNotCached() throws IOException {
    client.start();
    CheckRequest aCheck = newTestCheck();
    client.check(aCheck);
    verify(services, times(1)).check(TEST_SERVICE_NAME, aCheck);
    verify(checkStub, times(1)).execute();
  }

  @Test
  public void checkDoesNotInvokeTheTransportIfRequestIsCached() throws IOException {
    client.start();
    CheckRequest aCheck = newTestCheck();
    client.check(aCheck);
    reset(services);
    reset(checkStub);
    client.check(aCheck); // now it's cached
    verify(services, never()).check(TEST_SERVICE_NAME, aCheck);
    verify(checkStub, never()).execute();
  }

  @Test
  public void reportFailsIfNotStarted() {
    try {
      client.report(newTestReport());
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void reportInvokesTheTransportIfRequestIsNotCached() throws IOException {
    client.start();
    // This won't be cached because it's important
    ReportRequest aReport = newTestReport(TEST_SERVICE_NAME, Importance.HIGH, 1, 2);
    client.report(aReport);
    verify(services, times(1)).report(TEST_SERVICE_NAME, aReport);
    verify(reportStub, times(1)).execute();
  }

  @Test
  public void reportDoesNotInvokeTheTransportIfRequestIsAggregated() throws IOException {
    client.start();
    // This will be aggregated so no request will be sent
    ReportRequest aReport = newTestReport();
    client.report(aReport);
    verify(services, never()).report(TEST_SERVICE_NAME, aReport);
    verify(reportStub, never()).execute();
  }

  @Test
  public void startCreatesASchedulerEvenWhenSchedulerThreadCreationFails() throws IOException {
    reset(threads);
    when(threads.newThread(any(Runnable.class))).thenThrow(RuntimeException.class);
    client.start();
    verify(schedulers, times(1)).create(testTicker);
  }

  @Test
  public void checkSucceedsThoughSchedulerThreadCreationFailed() throws IOException {
    reset(threads);
    when(threads.newThread(any(Runnable.class))).thenThrow(RuntimeException.class);
    client.start();
    CheckRequest aCheck = newTestCheck();
    client.check(aCheck);
    verify(services, times(1)).check(TEST_SERVICE_NAME, aCheck);
    verify(checkStub, times(1)).execute();
  }

  @Test
  public void reportSucceedsThoughSchedulerThreadCreationFailed() throws IOException {
    reset(threads);
    when(threads.newThread(any(Runnable.class))).thenThrow(RuntimeException.class);
    client.start();
    ReportRequest aReport = newTestReport();
    client.report(aReport);
    verify(services, never()).report(TEST_SERVICE_NAME, aReport);
    verify(reportStub, never()).execute();
  }

  @Test
  public void reportsAreFlushedEvenThoughSchedulerThreadCreationFailed() throws IOException {
    reset(threads);
    when(threads.newThread(any(Runnable.class))).thenThrow(RuntimeException.class);
    client.start();
    ReportRequest aReport = newTestReport();
    client.report(aReport);
    testTicker.tick(1, TimeUnit.MINUTES); // longer than the default flush timeout
    client.report(aReport);
    verify(services, times(1)).report(eq(TEST_SERVICE_NAME), any(ReportRequest.class));
    verify(reportStub, times(1)).execute();
  }

  private ReportRequest newTestReport() {
    return newTestReport(TEST_SERVICE_NAME, Operation.Importance.LOW, 3, 0);
  }

  private CheckRequest newTestCheck() {
    return newTestCheck(TEST_SERVICE_NAME, Operation.Importance.LOW);
  }

  private static CheckRequest newTestCheck(String serviceName, Operation.Importance i) {
    if (i == null) {
      i = Operation.Importance.LOW;
    }
    Operation.Builder b = Operation
        .newBuilder()
        .setConsumerId(TEST_CONSUMER_ID)
        .setOperationName(TEST_OPERATION_NAME)
        .setImportance(i);
    return CheckRequest.newBuilder().setServiceName(serviceName).setOperation(b).build();
  }

  private ReportRequest newTestReport(String serviceName, Operation.Importance imp, int numOps,
      int opStartIndex) {
    Operation.Builder ob =
        Operation.newBuilder().setConsumerId(TEST_CONSUMER_ID).setImportance(imp);
    Builder b = ReportRequest.newBuilder();
    for (int i = 0; i < numOps; i++) {
      b.addOperations(ob.setOperationName(String.format("testOp%d", opStartIndex + i)));
    }
    return b.setServiceName(serviceName).build();
  }
}
