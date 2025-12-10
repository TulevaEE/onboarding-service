package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static org.bouncycastle.asn1.x509.Extension.certificatePolicies;
import static org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_clientAuth;
import static org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_emailProtection;

import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

public class WebEidCertificateFixture {

  private static final String VALID_ISSUER =
      "C=EE, O=SK ID Solutions AS, OID.2.5.4.97=NTREE-10747013, CN=ESTEID2018";
  private static final String AUTH_POLICY_OID = "0.4.0.2042.1.2";

  @SneakyThrows
  public static X509Certificate certificate(
      String firstName, String lastName, String personalCode, IdDocumentType documentType) {
    return generateCertificate(
        firstName, lastName, personalCode, documentType.getFirstIdentifier(), VALID_ISSUER, true);
  }

  @SneakyThrows
  public static X509Certificate certificateWithIssuer(
      String firstName, String lastName, String personalCode, String issuer) {
    return generateCertificate(
        firstName,
        lastName,
        personalCode,
        ESTONIAN_CITIZEN_ID_CARD.getFirstIdentifier(),
        issuer,
        true);
  }

  @SneakyThrows
  public static X509Certificate certificateWithoutClientAuth(
      String firstName, String lastName, String personalCode) {
    return generateCertificate(
        firstName,
        lastName,
        personalCode,
        ESTONIAN_CITIZEN_ID_CARD.getFirstIdentifier(),
        VALID_ISSUER,
        false);
  }

  private static X509Certificate generateCertificate(
      String firstName,
      String lastName,
      String personalCode,
      String documentTypeOid,
      String issuerDn,
      boolean includeClientAuth)
      throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    BigInteger serialNumber = new BigInteger(64, new SecureRandom());

    X500Name subjectDN =
        new X500Name(
            "C=EE, O=ESTEID, OU=AUTHENTICATION, "
                + "CN=\""
                + lastName
                + ","
                + firstName
                + ","
                + personalCode
                + "\", "
                + "SURNAME="
                + lastName
                + ", "
                + "GIVENNAME="
                + firstName
                + ", "
                + "SERIALNUMBER=PNOEE-"
                + personalCode);

    X500Name issuer = new X500Name(issuerDn);

    Date from = new Date();
    Date to = new Date(from.getTime() + 365 * 86400000L);

    SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
    AlgorithmIdentifier sigAlgId =
        new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WITHRSA");
    AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
    AsymmetricKeyParameter privateKeyAsymKeyParam =
        PrivateKeyFactory.createKey(privateKey.getEncoded());
    ContentSigner sigGen =
        new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);

    X509v3CertificateBuilder certGen =
        new X509v3CertificateBuilder(issuer, serialNumber, from, to, subjectDN, subPubKeyInfo);

    CertificatePolicies policies =
        new CertificatePolicies(
            new PolicyInformation[] {
              new PolicyInformation(new ASN1ObjectIdentifier(documentTypeOid)),
              new PolicyInformation(new ASN1ObjectIdentifier(AUTH_POLICY_OID))
            });
    certGen.addExtension(certificatePolicies, false, policies);

    if (includeClientAuth) {
      certGen.addExtension(
          Extension.extendedKeyUsage,
          false,
          new ExtendedKeyUsage(new KeyPurposeId[] {id_kp_clientAuth, id_kp_emailProtection}));
    }

    return new JcaX509CertificateConverter()
        .setProvider(new BouncyCastleProvider())
        .getCertificate(certGen.build(sigGen));
  }
}
