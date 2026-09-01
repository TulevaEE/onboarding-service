package ee.tuleva.onboarding.banking.seb;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Generates real, self-signed test certificates so SEB signing tests exercise genuine crypto. */
final class SebTestCertificates {

  private SebTestCertificates() {}

  static X509Certificate selfSigned(String distinguishedName, BigInteger serialNumber)
      throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    X500Name name = new X500Name(distinguishedName);
    Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
    Date notAfter = new Date(System.currentTimeMillis() + 86_400_000L);
    SubjectPublicKeyInfo publicKeyInfo =
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

    X509v3CertificateBuilder certificateBuilder =
        new X509v3CertificateBuilder(name, serialNumber, notBefore, notAfter, name, publicKeyInfo);
    ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
    X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);
    return new JcaX509CertificateConverter().getCertificate(certificateHolder);
  }
}
