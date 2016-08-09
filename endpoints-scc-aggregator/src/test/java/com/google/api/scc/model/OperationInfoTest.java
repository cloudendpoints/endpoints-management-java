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

import static org.junit.Assert.assertEquals;

import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.Operation.Importance;
import com.google.protobuf.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

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
  private static FakeClock TEST_CLOCK = new FakeClock();
  static {
    TEST_CLOCK.tick(2L, TimeUnit.SECONDS);
  }
  private static Timestamp REALLY_EARLY = Timestamps.now(TEST_CLOCK);
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
              .setImportance(Importance.LOW)
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
              .setImportance(Importance.LOW)
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
              .setImportance(Importance.LOW)
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
              .setImportance(Importance.LOW)
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
              .setImportance(Importance.LOW)
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
      assertEquals(t.want, t.given.asOperation(TEST_CLOCK));
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
}
