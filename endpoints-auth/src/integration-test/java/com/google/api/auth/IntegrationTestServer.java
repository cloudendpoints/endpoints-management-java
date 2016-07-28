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

package com.google.api.auth;

import com.google.common.base.Preconditions;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.net.URL;

/**
 * A REST server used by the integration tests.
 *
 * @author yangguan@google.com
 *
 */
final class IntegrationTestServer {

  private final int httpsPort;
  private final Server server;
  private final Class<?> resourceClass;

  public IntegrationTestServer(int httpsPort, Class<?> resourceClass) {
    Preconditions.checkNotNull(resourceClass);

    this.httpsPort = httpsPort;
    this.server = new Server();
    this.resourceClass = resourceClass;
  }

  public void start() throws Exception {
    URL keystoreUrl = IntegrationTestServer.class.getClassLoader().getResource("keystore.jks");
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(keystoreUrl.getPath());
    sslContextFactory.setKeyStorePassword("keystore");

    SecureRequestCustomizer src = new SecureRequestCustomizer();
    src.setStsMaxAge(2000);
    src.setStsIncludeSubDomains(true);

    HttpConfiguration httpsConfiguration = new HttpConfiguration();
    httpsConfiguration.setSecureScheme("https");
    httpsConfiguration.addCustomizer(src);

    ServerConnector https = new ServerConnector(server,
        new SslConnectionFactory(sslContextFactory,HttpVersion.HTTP_1_1.asString()),
            new HttpConnectionFactory(httpsConfiguration));
    https.setPort(this.httpsPort);

    this.server.setConnectors(new Connector[] { https });

    ResourceConfig resourceConfig = new ResourceConfig(this.resourceClass);
    ServletContainer servletContainer = new ServletContainer(resourceConfig);
    ServletHolder servletHolder = new ServletHolder(servletContainer);
    ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/*");
    servletContextHandler.addServlet(servletHolder, "/*");

    this.server.start();
  }

  public void stop() throws Exception {
    this.server.stop();
  }
}
