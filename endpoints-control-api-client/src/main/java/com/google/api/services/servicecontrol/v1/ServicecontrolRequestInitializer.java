/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://github.com/google/apis-client-generator/
 * Modify at your own risk.
 */

package com.google.api.services.servicecontrol.v1;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;

import java.io.IOException;


/**
 * Servicecontrol request initializer for setting properties like key and userIp.
 *
 * <p>
 * The simplest usage is to use it to set the key parameter:
 * </p>
 *
 * <pre>
  public static final GoogleClientRequestInitializer KEY_INITIALIZER =
      new ServicecontrolRequestInitializer(KEY);
 * </pre>
 *
 * <p>
 * There is also a constructor to set both the key and userIp parameters:
 * </p>
 *
 * <pre>
  public static final GoogleClientRequestInitializer INITIALIZER =
      new ServicecontrolRequestInitializer(KEY, USER_IP);
 * </pre>
 *
 * <p>
 * If you want to implement custom logic, extend it like this:
 * </p>
 *
 * <pre>
  public static class MyRequestInitializer extends ServicecontrolRequestInitializer {

    {@literal @}Override
    public void initializeServicecontrolRequest(ServicecontrolRequest{@literal <}?{@literal >} request)
        throws IOException {
      // custom logic
    }
  }
 * </pre>
 *
 * <p>
 * Finally, to set the key and userIp parameters and insert custom logic, extend it like this:
 * </p>
 *
 * <pre>
  public static class MyRequestInitializer2 extends ServicecontrolRequestInitializer {

    public MyKeyRequestInitializer() {
      super(KEY, USER_IP);
    }

    {@literal @}Override
    public void initializeServicecontrolRequest(ServicecontrolRequest{@literal <}?{@literal >} request)
        throws IOException {
      // custom logic
    }
  }
 * </pre>
 *
 * <p>
 * Subclasses should be thread-safe.
 * </p>
 *
 * @since 1.12
 */
@SuppressWarnings("unused")
public class ServicecontrolRequestInitializer extends CommonGoogleClientRequestInitializer {

  public ServicecontrolRequestInitializer() {
    super();
  }

  /**
   * @param key API key or {@code null} to leave it unchanged
   */
  public ServicecontrolRequestInitializer(String key) {
    super(key);
  }

  /**
   * @param key API key or {@code null} to leave it unchanged
   * @param userIp user IP or {@code null} to leave it unchanged
   */
  public ServicecontrolRequestInitializer(String key, String userIp) {
    super(key, userIp);
  }

  @Override
  public final void initialize(AbstractGoogleClientRequest<?> request) throws IOException {
    super.initialize(request);
    initializeServicecontrolRequest((ServicecontrolRequest<?>) request);
  }

  /**
   * Initializes Servicecontrol request.
   *
   * <p>
   * Default implementation does nothing. Called from
   * {@link #initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest)}.
   * </p>
   *
   * @throws java.io.IOException I/O exception
   */
  protected void initializeServicecontrolRequest(ServicecontrolRequest<?> request) throws java.io.IOException {
  }
}
