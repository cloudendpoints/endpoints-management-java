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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.api.client.util.Clock;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Builder;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

/**
 * CheckRequestInfoTest tests the behavior of {@code CheckRequestInfo}
 */
@RunWith(JUnit4.class)
public class CheckRequestInfoTest {
  private static final String TEST_REFERER = "aReferer";
  private static final String TEST_OPERATION_NAME = "anOperationName";
  private static final String TEST_OPERATION_ID = "anOperationId";
  private static final String TEST_SERVICE_NAME = "aServiceName";
  private static FakeClock TEST_CLOCK = new FakeClock();
  static {
    TEST_CLOCK.tick(2L, TimeUnit.SECONDS);
  }
  private static Timestamp REALLY_EARLY = Timestamps.now(TEST_CLOCK);
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
      assertEquals(t.want, t.given.asCheckRequest(TEST_CLOCK));
    }
  }

  @Test
  public void whenIncompleteShouldFailAsCheckRequest() {
    for (CheckRequestInfo i : INVALID_INFO) {
      try {
        i.asCheckRequest(Clock.SYSTEM);
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

  private static Builder newExpectedOperationBuilder() {
    return Operation
        .newBuilder()
        .setImportance(Importance.LOW)
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
