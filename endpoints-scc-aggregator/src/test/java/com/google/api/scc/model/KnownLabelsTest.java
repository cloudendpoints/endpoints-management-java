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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.api.LabelDescriptor;
import com.google.api.LabelDescriptor.ValueType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;


/**
 * KnownLabelsTest tests the behavior in {@code KnownLabels}
 */
@RunWith(JUnit4.class)
public class KnownLabelsTest {
  @Test
  public void shouldBeSupported() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldBeSupported();
    }
  }

  @Test
  public void shouldBeMatchCorrectly() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldMatchCorrectly();
    }
  }

  @Test
  public void shouldUpdateLabels() {
    for (StructuredTest t : ALL_TESTS) {
      t.shouldUpdateLabels();
    }
  }

  private static final StructuredTest[] ALL_TESTS =
      {
          // KnownLabels that do not yet do updates
          new StructuredTest(KnownLabels.CREDENTIAL_ID),
          new StructuredTest(KnownLabels.END_USER),
          new StructuredTest(KnownLabels.END_USER_COUNTRY),
          new StructuredTest(KnownLabels.GAE_CLONE_ID),
          new StructuredTest(KnownLabels.GAE_MODULE_ID),
          new StructuredTest(KnownLabels.GAE_REPLICA_INDEX),
          new StructuredTest(KnownLabels.GAE_VERSION_ID),
          new StructuredTest(KnownLabels.GCP_PROJECT),
          new StructuredTest(KnownLabels.GCP_PROJECT),
          new StructuredTest(KnownLabels.GCP_REGION),
          new StructuredTest(KnownLabels.GCP_RESOURCE_ID),
          new StructuredTest(KnownLabels.GCP_RESOURCE_TYPE),
          new StructuredTest(KnownLabels.GCP_SERVICE),
          new StructuredTest(KnownLabels.GCP_ZONE),
          new StructuredTest(KnownLabels.GCP_UID),
          new StructuredTest(KnownLabels.SCC_REFERER),

          // Credential label test
          // TODO: add tests for when issuer and audience are present
          new StructuredTest(KnownLabels.CREDENTIAL_ID,
              ImmutableMap.<String, String>of(KnownLabels.CREDENTIAL_ID.getName(),
                  "apiKey:testApiKey"),
              new ReportRequestInfo(new OperationInfo().setApiKey("testApiKey"))),

          // Status Code
          new StructuredTest(KnownLabels.STATUS_CODE,
              ImmutableMap.<String, String>of(KnownLabels.STATUS_CODE.getName(), "0"),
              new ReportRequestInfo().setResponseCode(200)),
          new StructuredTest(KnownLabels.STATUS_CODE,
              ImmutableMap.<String, String>of(KnownLabels.STATUS_CODE.getName(), "16"),
              new ReportRequestInfo().setResponseCode(401)),
          new StructuredTest(KnownLabels.STATUS_CODE,
              ImmutableMap.<String, String>of(KnownLabels.STATUS_CODE.getName(), "9"),
              new ReportRequestInfo().setResponseCode(477)), // unknown client failure
          new StructuredTest(KnownLabels.STATUS_CODE,
              ImmutableMap.<String, String>of(KnownLabels.STATUS_CODE.getName(), "13"),
              new ReportRequestInfo().setResponseCode(577)), // unknown server failure
          new StructuredTest(KnownLabels.STATUS_CODE,
              ImmutableMap.<String, String>of(KnownLabels.STATUS_CODE.getName(), "2"),
              new ReportRequestInfo().setResponseCode(777)), // complete unknown code

          // Other labels
          new StructuredTest(KnownLabels.ERROR_TYPE,
              ImmutableMap.<String, String>of(KnownLabels.ERROR_TYPE.getName(), "2XX"),
              new ReportRequestInfo().setResponseCode(200)),
          new StructuredTest(KnownLabels.RESPONSE_CODE,
              ImmutableMap.<String, String>of(KnownLabels.RESPONSE_CODE.getName(), "200"),
              new ReportRequestInfo().setResponseCode(200)),
          new StructuredTest(KnownLabels.RESPONSE_CODE_CLASS,
              ImmutableMap.<String, String>of(KnownLabels.RESPONSE_CODE_CLASS.getName(), "2XX"),
              new ReportRequestInfo().setResponseCode(200)),
          new StructuredTest(KnownLabels.PROTOCOL,
              ImmutableMap.<String, String>of(KnownLabels.PROTOCOL.getName(), "UNKNOWN")),
          new StructuredTest(KnownLabels.REFERER,
              ImmutableMap.<String, String>of(KnownLabels.REFERER.getName(),
                  StructuredTest.TEST_REFERER)),
          new StructuredTest(KnownLabels.SVC_API_VERSION, ImmutableMap.<String, String>of(
              KnownLabels.SVC_API_VERSION.getName(), StructuredTest.TEST_VERSION)),
          new StructuredTest(KnownLabels.SVC_API_METHOD,
              ImmutableMap.<String, String>of(KnownLabels.SVC_API_METHOD.getName(),
                  StructuredTest.TEST_METHOD)),
          new StructuredTest(KnownLabels.GCP_LOCATION, ImmutableMap.<String, String>of(
              KnownLabels.GCP_LOCATION.getName(), StructuredTest.TEST_LOCATION))
      };

  static class StructuredTest {
    private static final String TEST_LOCATION = "location";
    private static final String TEST_METHOD = "aMethod";
    private static final String TEST_VERSION = "apiVersion";
    private static final String TEST_REFERER = "aReferer";
    StructuredTest() {
      given = new ReportRequestInfo(new OperationInfo().setReferer(TEST_REFERER))
          .setApiMethod(TEST_METHOD)
          .setApiVersion(TEST_VERSION)
          .setLocation(TEST_LOCATION);
    }

    StructuredTest(KnownLabels subject) {
      this();
      this.subject = subject;
    }

    StructuredTest(KnownLabels subject, Map<String, String> wanted) {
      this(subject);
      this.wantedLabels = wanted;
    }


    StructuredTest(KnownLabels subject, Map<String, String> wanted, ReportRequestInfo given) {
      this(subject, wanted);
      this.given = given;
    }

    void shouldBeSupported() {
      Assert.assertTrue(KnownLabels.isSupported(matchingDescriptor().build()));
      Assert.assertFalse(KnownLabels.isSupported(notMatched().build()));
    }

    void shouldMatchCorrectly() {
      Assert.assertTrue(subject.matches(matchingDescriptor().build()));
      Assert.assertFalse(subject.matches(notMatched().build()));
    }

    void shouldUpdateLabels() {
      Map<String, String> givenLabels = Maps.newHashMap();
      subject.performUpdate(given, givenLabels);
      if (wantedLabels != null) {
        Assert.assertEquals(wantedLabels, givenLabels);
      }
    }

    LabelDescriptor.Builder matchingDescriptor() {
      return LabelDescriptor.newBuilder().setKey(subject.getName()).setValueType(subject.getType());
    }

    LabelDescriptor.Builder notMatched() {
      LabelDescriptor.Builder result = matchingDescriptor();
      result.setValueType(ValueType.BOOL);
      return result;
    }

    KnownLabels subject;
    Map<String, String> wantedLabels;
    ReportRequestInfo given;
  }
}
