package com.google.api.control.model;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.Http;
import com.google.api.HttpRule;
import com.google.api.Service;
import com.google.api.control.model.MethodRegistry.Info;

import org.junit.Test;

/**
 * Tests for {@link MethodRegistry}.
 */
public class MethodRegistryTest {
  private static final String SELECTOR = "a.selector";
  private static final String SLASH_SELECTOR = "slash.selector";
  private static final Service SERVICE = Service.newBuilder()
      .setName("the-service")
      .setHttp(Http.newBuilder()
          .addRules(HttpRule.newBuilder()
              .setSelector(SELECTOR)
              .setGet("/v1/foo/{bar}/baz"))
          .addRules(HttpRule.newBuilder()
              .setSelector(SLASH_SELECTOR)
              .setGet("/v1/baz/{bar}/foo/")))
      .build();

  @Test
  public void lookup_matchesWithOrWithoutTrailingSlashes() {
    MethodRegistry r = new MethodRegistry(SERVICE);
    assertSuccess(r.lookup("GET", "/v1/foo/2/baz"), SELECTOR);
    assertSuccess(r.lookup("GET", "/v1/foo/2/baz/"), SELECTOR);
    assertFailure(r.lookup("POST", "/v1/foo/2/baz"));
    assertFailure(r.lookup("POST", "/v1/foo/2/baz/"));

    assertSuccess(r.lookup("GET", "/v1/baz/2/foo"), SLASH_SELECTOR);
    assertSuccess(r.lookup("GET", "/v1/baz/2/foo/"), SLASH_SELECTOR);
    assertFailure(r.lookup("POST", "/v1/baz/2/foo"));
    assertFailure(r.lookup("POST", "/v1/baz/2/foo/"));
  }

  private static void assertFailure(Info info) {
    assertThat(info).isNull();
  }

  private static void assertSuccess(Info info, String selector) {
    assertThat(info).isNotNull();
    assertThat(info.getSelector()).isEqualTo(selector);
  }
}
