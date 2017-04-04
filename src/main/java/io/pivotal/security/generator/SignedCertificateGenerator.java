package io.pivotal.security.generator;

import io.pivotal.security.controller.v1.CertificateSecretParameters;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class SignedCertificateGenerator {

  private final DateTimeProvider timeProvider;
  private final RandomSerialNumberGenerator serialNumberGenerator;
  private final BouncyCastleProvider provider;
  private final X509ExtensionUtils x509ExtensionUtils;

  @Autowired
  SignedCertificateGenerator(
      DateTimeProvider timeProvider,
      RandomSerialNumberGenerator serialNumberGenerator,
      BouncyCastleProvider provider
  ) throws Exception {
    this.timeProvider = timeProvider;
    this.serialNumberGenerator = serialNumberGenerator;
    this.provider = provider;
    this.x509ExtensionUtils = new X509ExtensionUtils(new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)));
  }

  X509Certificate getSelfSigned(KeyPair keyPair, CertificateSecretParameters params)
      throws Exception {
    return getSignedByIssuer(params.getDn(), keyPair.getPrivate(), keyPair, params);
  }

  X509Certificate getSignedByIssuer(
      X500Name issuerDn,
      PrivateKey issuerKey,
      KeyPair keyPair,
      CertificateSecretParameters params) throws Exception {
    Instant now = timeProvider.getNow().toInstant();
    SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo
        .getInstance(keyPair.getPublic().getEncoded());

    final X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
        issuerDn,
        serialNumberGenerator.generate(),
        Date.from(now),
        Date.from(now.plus(Duration.ofDays(params.getDurationDays()))),
        params.getDn(),
        publicKeyInfo
    );

    certificateBuilder.addExtension(Extension.subjectKeyIdentifier, false, x509ExtensionUtils.createSubjectKeyIdentifier(publicKeyInfo));
    if (params.getAlternativeNames() != null) {
      certificateBuilder
          .addExtension(Extension.subjectAlternativeName, false, params.getAlternativeNames());
    }

    if (params.getKeyUsage() != null) {
      certificateBuilder.addExtension(Extension.keyUsage, true, params.getKeyUsage());
    }

    if (params.getExtendedKeyUsage() != null) {
      certificateBuilder
          .addExtension(Extension.extendedKeyUsage, false, params.getExtendedKeyUsage());
    }

    certificateBuilder
        .addExtension(Extension.basicConstraints, true, new BasicConstraints(params.isCa()));

    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider)
        .build(issuerKey);

    X509CertificateHolder holder = certificateBuilder.build(contentSigner);

    return new JcaX509CertificateConverter().setProvider(provider).getCertificate(holder);
  }
}
