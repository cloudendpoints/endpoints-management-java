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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.servicecontrol.v1.CheckError;
import com.google.api.servicecontrol.v1.CheckError.Code;
import com.google.api.servicecontrol.v1.CheckResponse;

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
