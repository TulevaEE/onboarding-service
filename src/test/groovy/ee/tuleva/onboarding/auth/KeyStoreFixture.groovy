package ee.tuleva.onboarding.auth;

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate

class KeyStoreFixture {

  static final KeyPair keyPair

  static {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    keyPair = keyPairGenerator.generateKeyPair()
  }

  static Resource keyStore() {

    // Create a self-signed X.509 certificate
    X509Certificate certificate = generateSelfSignedCertificate(keyPair)

    final var keyStore = KeyStore.getInstance("pkcs12")
    keyStore.load(null, null)
    keyStore.setKeyEntry("jwt", keyPair.getPrivate(), keyStorePassword, new X509Certificate[]{certificate})
    final var bout = new ByteArrayOutputStream()
    keyStore.store(bout, keyStorePassword)
    return new ByteArrayResource(bout.toByteArray())
  }

  private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) {
    // Create a self-signed X.509 certificate using X509v3CertificateBuilder
    X500Name issuer = new X500Name("CN=SelfSignedCA");
    X500Name subject = new X500Name("CN=SelfSignedCertificate");
    BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
    Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000); // Yesterday
    Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000); // One year

    SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

    X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
        issuer,
        serialNumber,
        notBefore,
        notAfter,
        subject,
        publicKeyInfo
    )

    // Build the certificate
    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
        .build(keyPair.getPrivate())
    X509CertificateHolder certHolder = certBuilder.build(contentSigner);
    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }

  public static char[] keyStorePassword = "changeit".toCharArray()
}
