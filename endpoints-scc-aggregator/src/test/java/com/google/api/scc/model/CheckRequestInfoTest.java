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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Builder;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;

/**
 * CheckRequestInfoTest tests the behavior of {@code CheckRequestInfo}
 */
@RunWith(JUnit4.class)
public class CheckRequestInfoTest {
  private static final String TEST_REFERER = "aReferer";
  private static final String TEST_OPERATION_NAME = "anOperationName";
  private static final String TEST_OPERATION_ID = "anOperationId";
  private static final String TEST_SERVICE_NAME = "aServiceName";
  private static FakeTicker TEST_TICKER = new FakeTicker();
  static {
    TEST_TICKER.tick(2L, TimeUnit.SECONDS);
  }
  private static Timestamp REALLY_EARLY = Timestamps.now(TEST_TICKER);
  private static final String TEST_CLIENT_IP = "127.0.0.1";
  private static final CheckRequestInfo[] INVALID_INFO = {
      new CheckRequestInfo(
          new OperationInfo().setServiceName(TEST_SERVICE_NAME).setOperationId(TEST_OPERATION_ID)),
      new CheckRequestInfo(new OperationInfo()
          .setServiceName(TEST_SERVICE_NAME)
          .setOperationName(TEST_OPERATION_NAME)),
      new CheckRequestInfo(new OperationInfo()
          .setOperationName(TEST_OPERATION_NAME)
          .setOperationId(TEST_OPERATION_ID)),};
  public static final InfoTest[] AS_CHECK_REQUEST_TEST = {
      new InfoTest(new CheckRequestInfo(newTestOperationInfo().setReferer(TEST_REFERER)),
          CheckRequest
              .newBuilder()
              .setServiceName(TEST_SERVICE_NAME)
              .setOperation(newExpectedOperationBuilder().putAllLabels(
                  ImmutableMap.of(CheckRequestInfo.SCC_USER_AGENT, KnownLabels.USER_AGENT,
                      CheckRequestInfo.SCC_REFERER, TEST_REFERER)))
              .build()),
      new InfoTest(new CheckRequestInfo(newTestOperationInfo()).setClientIp(TEST_CLIENT_IP),
          CheckRequest
              .newBuilder()
              .setServiceName(TEST_SERVICE_NAME)
              .setOperation(newExpectedOperationBuilder()
                  .putAllLabels(ImmutableMap.of(CheckRequestInfo.SCC_USER_AGENT,
                      KnownLabels.USER_AGENT, CheckRequestInfo.SCC_CALLER_IP, TEST_CLIENT_IP)))
              .build())

  };

  @Test
  public void test() {
    for (InfoTest t : AS_CHECK_REQUEST_TEST) {
      assertEquals(t.want, t.given.asCheckRequest(TEST_TICKER));
    }
  }

  @Test
  public void whenIncompleteShouldFailAsCheckRequest() {
    for (CheckRequestInfo i : INVALID_INFO) {
      try {
        i.asCheckRequest(Ticker.systemTicker());
        fail("Should have raised IllegalStateException");
      } catch (IllegalStateException e) {
        // expected
      }
    }
  }

  private static class InfoTest {
    CheckRequestInfo given;
    CheckRequest want;

    public InfoTest(CheckRequestInfo given, CheckRequest want) {
      this.given = given;
      this.want = want;
    }
  }

  private static class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    /** Advances the ticker value by {@code time} in {@code timeUnit}. */
    public FakeTicker tick(long time, TimeUnit timeUnit) {
      nanos.addAndGet(timeUnit.toNanos(time));
      return this;
    }

    @Override
    public long read() {
      return nanos.getAndAdd(0);
    }
  }

  private static Builder newExpectedOperationBuilder() {
    return Operation
        .newBuilder()
        .setOperationName(TEST_OPERATION_NAME)
        .setOperationId(TEST_OPERATION_ID)
        .setEndTime(REALLY_EARLY)
        .setStartTime(REALLY_EARLY);
  }

  private static OperationInfo newTestOperationInfo() {
    return new OperationInfo()
        .setOperationId(TEST_OPERATION_ID)
        .setOperationName(TEST_OPERATION_NAME)
        .setServiceName(TEST_SERVICE_NAME);
  }
}
