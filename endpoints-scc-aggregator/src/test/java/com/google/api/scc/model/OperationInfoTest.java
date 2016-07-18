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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.servicecontrol.v1.Operation;
import com.google.common.base.Ticker;
import com.google.protobuf.Timestamp;

/**
 * OperationInfoTest tests the behavior of {@code OperationInfo}
 */
@RunWith(JUnit4.class)
public class OperationInfoTest {
  private static final String TEST_PROJECT_ID = "aProjectId";
  private static final String TEST_API_KEY = "anApiKey";
  private static final String TEST_SERVICE_NAME = "aService";
  private static final String TEST_REFERER = "aReferer";
  private static final String TEST_OPERATION_NAME = "anOperationName";
  private static final String TEST_OPERATION_ID = "anOperationId";
  private static FakeTicker TEST_TICKER = new FakeTicker();
  static {
    TEST_TICKER.tick(2L, TimeUnit.SECONDS);
  }
  private static Timestamp REALLY_EARLY = Timestamps.now(TEST_TICKER);
  private static final InfoTest[] AS_OPERATION_TEST = {
      new InfoTest(
          new OperationInfo()
              .setReferer(TEST_REFERER)
              .setOperationId(TEST_OPERATION_ID)
              .setServiceName(TEST_SERVICE_NAME)
              .setApiKey(TEST_API_KEY)
              .setApiKeyValid(false)
              .setConsumerProjectId(TEST_PROJECT_ID),
          Operation
              .newBuilder()
              .setEndTime(REALLY_EARLY)
              .setOperationId(TEST_OPERATION_ID)
              .setStartTime(REALLY_EARLY)
              .setConsumerId("project:" + TEST_PROJECT_ID)
              .build()),
      new InfoTest(
          new OperationInfo()
              .setReferer(TEST_REFERER)
              .setOperationId(TEST_OPERATION_ID)
              .setServiceName(TEST_SERVICE_NAME)
              .setApiKey(TEST_API_KEY)
              .setApiKeyValid(false),
          Operation
              .newBuilder()
              .setEndTime(REALLY_EARLY)
              .setOperationId(TEST_OPERATION_ID)
              .setStartTime(REALLY_EARLY)
              .build()),
      new InfoTest(
          new OperationInfo()
              .setReferer(TEST_REFERER)
              .setOperationId(TEST_OPERATION_ID)
              .setServiceName(TEST_SERVICE_NAME)
              .setApiKey(TEST_API_KEY)
              .setApiKeyValid(true),
          Operation
              .newBuilder()
              .setEndTime(REALLY_EARLY)
              .setOperationId(TEST_OPERATION_ID)
              .setStartTime(REALLY_EARLY)
              .setConsumerId("api_key:" + TEST_API_KEY)
              .build()),
      new InfoTest(
          new OperationInfo()
              .setReferer(TEST_REFERER)
              .setOperationId(TEST_OPERATION_ID)
              .setServiceName(TEST_SERVICE_NAME),
          Operation
              .newBuilder()
              .setEndTime(REALLY_EARLY)
              .setOperationId(TEST_OPERATION_ID)
              .setStartTime(REALLY_EARLY)
              .build()),
      new InfoTest(
          new OperationInfo()
              .setReferer(TEST_REFERER)
              .setOperationName(TEST_OPERATION_NAME)
              .setOperationId(TEST_OPERATION_ID)
              .setServiceName(TEST_SERVICE_NAME),
          Operation
              .newBuilder()
              .setEndTime(REALLY_EARLY)
              .setOperationName(TEST_OPERATION_NAME)
              .setOperationId(TEST_OPERATION_ID)
              .setStartTime(REALLY_EARLY)
              .build()),
      new InfoTest(new OperationInfo().setReferer(TEST_REFERER).setServiceName(TEST_SERVICE_NAME),
          Operation.newBuilder().setEndTime(REALLY_EARLY).setStartTime(REALLY_EARLY).build()
      ),
  };

  @Test
  public void test() {
    for (InfoTest t : AS_OPERATION_TEST) {
      assertEquals(t.want, t.given.asOperation(TEST_TICKER));
    }
  }

  private static class InfoTest {
    OperationInfo given;
    Operation want;

    InfoTest(OperationInfo given, Operation want) {
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
}
