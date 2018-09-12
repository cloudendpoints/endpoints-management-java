package com.google.api.control;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.Vector;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigFilterTest {
  @Mock private HttpServletRequest request;

  @Before
  public void setUp() {
  }

  @Test
  public void getRequestMethodOverride() {
    Vector<String> headers = new Vector<>();
    headers.add("x-http-method-override");
    when(request.getHeaderNames()).thenReturn(headers.elements());
    when(request.getHeader("x-http-method-override")).thenReturn("post");
    assertThat(ConfigFilter.getRequestMethodOverride(request)).isEqualTo("POST");
  }

  @Test
  public void getRequestMethodOverride_noHeader() {
    Vector<String> headers = new Vector<>();
    when(request.getHeaderNames()).thenReturn(headers.elements());
    assertThat(ConfigFilter.getRequestMethodOverride(request)).isNull();
  }

  @Test
  public void getRealHttpMethod() {
    when(request.getAttribute(ConfigFilter.HTTP_METHOD_OVERRIDE_ATTRIBUTE)).thenReturn("foo");
    assertThat(ConfigFilter.getRealHttpMethod(request)).isEqualTo("foo");
  }

  @Test
  public void getRealHttpMethod_noAttribute() {
    when(request.getMethod()).thenReturn("bar");
    assertThat(ConfigFilter.getRealHttpMethod(request)).isEqualTo("bar");
  }
}
