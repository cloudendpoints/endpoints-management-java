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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;

import com.google.api.servicecontrol.v1.CheckError;
import com.google.api.servicecontrol.v1.CheckError.Code;
import com.google.api.servicecontrol.v1.CheckResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * CheckErrorInfoTest tests the behavior in {@code CheckErrorInfo}
 *
 */
@RunWith(JUnit4.class)
public class CheckErrorInfoTest {
  private static final String TEST_DETAIL = "my-detail";
  private static final String TEST_PROJECT = "my-project";

  @Test
  public void shouldBeOkWhenThereAreNoErrors() {
    CheckResponse noErrors = CheckResponse.newBuilder().build();
    CheckErrorInfo converted = CheckErrorInfo.convert(noErrors);
    assertEquals(CheckErrorInfo.OK, converted);
    assertEquals(SC_OK, converted.getHttpCode());
    assertEquals("", converted.fullMessage(TEST_PROJECT, TEST_DETAIL));
  }

  @Test
  public void shouldBeServiceUnavailableIfResponseIsNull() {
    CheckErrorInfo converted = CheckErrorInfo.convert(null);
    assertEquals(CheckErrorInfo.SERVICE_STATUS_UNAVAILABLE, converted);
    assertEquals(SC_OK, converted.getHttpCode());
    assertEquals("", converted.fullMessage(TEST_PROJECT, TEST_DETAIL));
  }

  @Test
  public void shouldIncludeTheProjectIdInFullMessage() {
    CheckResponse deleted = CheckResponse
        .newBuilder()
        .addCheckErrors(CheckError.newBuilder().setCode(Code.PROJECT_DELETED))
        .build();
    CheckErrorInfo converted = CheckErrorInfo.convert(deleted);
    assertEquals(CheckErrorInfo.PROJECT_DELETED, converted);
    assertEquals("Project my-project has been deleted",
        converted.fullMessage(TEST_PROJECT, TEST_DETAIL));
  }

  @Test
  public void shouldIncludeDetailInFullMessage() {
    CheckResponse deleted = CheckResponse
        .newBuilder()
        .addCheckErrors(CheckError.newBuilder().setCode(Code.IP_ADDRESS_BLOCKED))
        .build();
    CheckErrorInfo converted = CheckErrorInfo.convert(deleted);
    assertEquals(CheckErrorInfo.IP_ADDRESS_BLOCKED, converted);
    assertEquals(TEST_DETAIL, converted.fullMessage(TEST_PROJECT, TEST_DETAIL));
  }
}
