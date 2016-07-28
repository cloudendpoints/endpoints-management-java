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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;

import java.math.BigInteger;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;

/**
 * Testing utilites.
 *
 * @author yangguan@google.com
 *
 */
public final class TestUtils {
  private TestUtils() {
    // no instantiation
  }

  /**
   * Generate an auth token with the given claims and sign the token with the
   * private key in the provided {@link RsaJsonWebKey}. Set the auth token to
   * expire in 5 minutes.
   */
  public static String generateAuthToken(
      Optional<Collection<String>> audiences,
      Optional<String> email,
      Optional<String> issuer,
      Optional<String> subject,
      RsaJsonWebKey rsaJsonWebKey) {

    NumericDate expirationTime = NumericDate.now();
    expirationTime.addSeconds(5 * 30);
    return generateAuthToken(
        audiences,
        email,
        expirationTime,
        issuer,
        NumericDate.now(),
        subject,
        rsaJsonWebKey);
  }

  /**
   * Generate an auth token with the given claims and sign the token with the
   * private key in the provided {@link RsaJsonWebKey}.
   */
  public static String generateAuthToken(
      Optional<Collection<String>> audiences,
      Optional<String> email,
      NumericDate expirationTime,
      Optional<String> issuer,
      NumericDate notBefore,
      Optional<String> subject,
      RsaJsonWebKey rsaJsonWebKey) {
    JwtClaims claims = new JwtClaims();
    if (audiences.isPresent()) {
      claims.setAudience(ImmutableList.copyOf(audiences.get()));
    }
    if (email.isPresent()) {
      claims.setClaim("email", email.get());
    }
    if (issuer.isPresent()) {
      claims.setIssuer(issuer.get());
    }
    if (subject.isPresent()) {
      claims.setSubject(subject.get());
    }
    claims.setExpirationTime(expirationTime);
    claims.setNotBefore(notBefore);

    JsonWebSignature jsonWebSignature = new JsonWebSignature();
    jsonWebSignature.setPayload(claims.toJson());
    jsonWebSignature.setKey(rsaJsonWebKey.getPrivateKey());
    jsonWebSignature.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
    jsonWebSignature.setAlgorithmHeaderValue(rsaJsonWebKey.getAlgorithm());

    try {
      return jsonWebSignature.getCompactSerialization();
    } catch (JoseException exception) {
      throw new RuntimeException("failed to generate JWT", exception);
    }
  }

  /**
   * Generate a {@link RsaJsonWebKey}.
   */
  public static RsaJsonWebKey generateRsaJsonWebKey(String keyId) {
    try {
      RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
      rsaJsonWebKey.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA256);
      rsaJsonWebKey.setKeyId(keyId);
      return rsaJsonWebKey;
    } catch (JoseException exception) {
      throw new RuntimeException("failed to generate RSA Json web key", exception);
    }
  }

  /**
   * Generate a PEM-encoded X509 using the given {@link RsaJsonWebKey}.
   */
  public static String generateX509Cert(RsaJsonWebKey rsaJsonWebKey) {
    try {
      Provider provider = new BouncyCastleProvider();
      String providerName = provider.getName();
      Security.addProvider(provider);

      long currentTimeMillis = System.currentTimeMillis();
      Date start = new Date(currentTimeMillis - Duration.ofDays(1).toMillis());
      Date end = new Date(currentTimeMillis + Duration.ofDays(1).toMillis());
      X509v3CertificateBuilder x509v3CertificateBuilder = new X509v3CertificateBuilder(
          new X500Name("cn=example"),
          BigInteger.valueOf(currentTimeMillis),
          start,
          end,
          new X500Name("cn=example"),
          SubjectPublicKeyInfo.getInstance(rsaJsonWebKey.getPublicKey().getEncoded()));
      ContentSigner contentSigner = new JcaContentSignerBuilder("SHA1WithRSAEncryption")
          .setProvider(providerName)
          .build(rsaJsonWebKey.getPrivateKey());
      X509CertificateHolder x509CertHolder = x509v3CertificateBuilder.build(contentSigner);
      X509Certificate certificate =
          new JcaX509CertificateConverter().getCertificate(x509CertHolder);
      Security.removeProvider(providerName);

      return String.format("%s%n%s%n%s",
          DefaultJwksSupplier.X509_CERT_PREFIX,
          new X509Util().toPem(certificate),
          DefaultJwksSupplier.X509_CERT_SUFFIX);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
