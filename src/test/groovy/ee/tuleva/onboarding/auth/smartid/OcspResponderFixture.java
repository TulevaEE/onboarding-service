package ee.tuleva.onboarding.auth.smartid;

import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

final class OcspResponderFixture {

  static final String RESPONDER_URL = "http://aia.example.test/eidq2024";

  private static final AtomicLong SERIALS = new AtomicLong(1000);

  final KeyPair caKeys = keyPair();
  final X509Certificate ca = caCertificate("CN=TEST of Smart-ID issuing CA", caKeys);
  final KeyPair responderKeys = keyPair();
  final X509Certificate responder =
      issuedBy(ca, caKeys, "CN=TEST of Smart-ID OCSP responder", responderKeys, true);
  final X509Certificate authenticationCertificate =
      authenticationCertificateIssuedBy(ca, caKeys, RESPONDER_URL);

  record Answer(
      CertificateStatus status,
      Instant thisUpdate,
      boolean echoNonce,
      KeyPair signingKeys,
      X509Certificate signingCertificate,
      BigInteger serial) {}

  Answer answering(CertificateStatus status, Instant thisUpdate) {
    return new Answer(
        status,
        thisUpdate,
        true,
        responderKeys,
        responder,
        authenticationCertificate.getSerialNumber());
  }

  byte[] respond(byte[] encodedRequest, Answer answer) {
    try {
      OCSPReq request = new OCSPReq(encodedRequest);
      CertificateID id =
          new CertificateID(sha1(), new JcaX509CertificateHolder(ca), answer.serial());
      var builder = new JcaBasicOCSPRespBuilder(answer.signingCertificate().getPublicKey(), sha1());
      if (answer.echoNonce()) {
        builder.setResponseExtensions(new Extensions(request.getExtension(id_pkix_ocsp_nonce)));
      }
      builder.addResponse(id, answer.status(), Date.from(answer.thisUpdate()), (Date) null);
      BasicOCSPResp basic =
          builder.build(
              signer(answer.signingKeys()),
              new X509CertificateHolder[] {
                new JcaX509CertificateHolder(answer.signingCertificate())
              },
              Date.from(answer.thisUpdate()));
      return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).getEncoded();
    } catch (IOException | OCSPException | GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] unsuccessful(int status) {
    try {
      return new OCSPRespBuilder().build(status, null).getEncoded();
    } catch (IOException | OCSPException e) {
      throw new IllegalStateException(e);
    }
  }

  static KeyPair keyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static X509Certificate caCertificate(String subject, KeyPair keys) {
    X500Name name = new X500Name(subject);
    var builder =
        new JcaX509v3CertificateBuilder(
            name, serial(), notBefore(), notAfter(), name, keys.getPublic());
    try {
      builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return certificate(builder, keys);
  }

  static X509Certificate issuedBy(
      X509Certificate issuer,
      KeyPair issuerKeys,
      String subject,
      KeyPair keys,
      boolean ocspSigning) {
    var builder =
        new JcaX509v3CertificateBuilder(
            issuer, serial(), notBefore(), notAfter(), new X500Name(subject), keys.getPublic());
    if (ocspSigning) {
      try {
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_OCSPSigning));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return certificate(builder, issuerKeys);
  }

  static X509Certificate authenticationCertificateIssuedBy(
      X509Certificate issuer, KeyPair issuerKeys, String responderUrl) {
    var builder =
        new JcaX509v3CertificateBuilder(
            issuer,
            serial(),
            notBefore(),
            notAfter(),
            new X500Name("CN=TEST OF SMART-ID PERSON,SERIALNUMBER=PNOEE-38888888888,C=EE"),
            keyPair().getPublic());
    try {
      builder.addExtension(
          Extension.authorityInfoAccess,
          false,
          new AuthorityInformationAccess(
              new AccessDescription(
                  AccessDescription.id_ad_ocsp,
                  new GeneralName(
                      GeneralName.uniformResourceIdentifier, new DERIA5String(responderUrl)))));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return certificate(builder, issuerKeys);
  }

  private static X509Certificate certificate(X509v3CertificateBuilder builder, KeyPair signerKeys) {
    try {
      return new JcaX509CertificateConverter().getCertificate(builder.build(signer(signerKeys)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ContentSigner signer(KeyPair keys) {
    try {
      return new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate());
    } catch (OperatorCreationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static DigestCalculator sha1() {
    try {
      return new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1);
    } catch (OperatorCreationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BigInteger serial() {
    return BigInteger.valueOf(SERIALS.incrementAndGet());
  }

  private static Date notBefore() {
    return Date.from(Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static Date notAfter() {
    return Date.from(Instant.parse("2029-01-01T00:00:00Z"));
  }
}
