package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static org.bouncycastle.asn1.x509.Extension.certificatePolicies;
import static org.bouncycastle.asn1.x509.Extension.extendedKeyUsage;
import static org.bouncycastle.asn1.x509.Extension.keyUsage;
import static org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_clientAuth;
import static org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_emailProtection;
import static org.bouncycastle.asn1.x509.KeyUsage.digitalSignature;

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
import org.bouncycastle.asn1.x509.KeyUsage;
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

  public static X509Certificate certificate(
      String firstName, String lastName, String personalCode, IdDocumentType documentType) {
    return buildCertificate(
        subjectDn(firstName, lastName, personalCode),
        VALID_ISSUER,
        policies(documentType.getFirstIdentifier()),
        clientAuthentication());
  }

  public static X509Certificate certificateWithIssuer(
      String firstName, String lastName, String personalCode, String issuer) {
    return buildCertificate(
        subjectDn(firstName, lastName, personalCode),
        issuer,
        policies(ESTONIAN_CITIZEN_ID_CARD.getFirstIdentifier()),
        clientAuthentication());
  }

  public static X509Certificate certificateWithoutClientAuth(
      String firstName, String lastName, String personalCode) {
    return buildCertificate(
        subjectDn(firstName, lastName, personalCode),
        VALID_ISSUER,
        policies(ESTONIAN_CITIZEN_ID_CARD.getFirstIdentifier()));
  }

  public static X509Certificate certificateWithoutPolicies(
      String firstName, String lastName, String personalCode) {
    return buildCertificate(
        subjectDn(firstName, lastName, personalCode),
        VALID_ISSUER,
        signing(),
        clientAuthentication());
  }

  // A subject DN missing one of SURNAME=, GIVENNAME= or SERIALNUMBER= exercises the
  // "attribute missing from certificate" error paths in WebEidAuthService#createSession.
  public static X509Certificate certificateWithSubjectDn(String subjectDn) {
    return buildCertificate(
        new X500Name(subjectDn),
        VALID_ISSUER,
        policies(ESTONIAN_CITIZEN_ID_CARD.getFirstIdentifier()),
        clientAuthentication());
  }

  private static X500Name subjectDn(String firstName, String lastName, String personalCode) {
    return new X500Name(
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
  }

  @SneakyThrows
  private static Extension policies(String documentTypeOid) {
    return Extension.create(
        certificatePolicies,
        false,
        new CertificatePolicies(
            new PolicyInformation[] {
              new PolicyInformation(new ASN1ObjectIdentifier(documentTypeOid)),
              new PolicyInformation(new ASN1ObjectIdentifier(AUTH_POLICY_OID))
            }));
  }

  @SneakyThrows
  private static Extension clientAuthentication() {
    return Extension.create(
        extendedKeyUsage,
        false,
        new ExtendedKeyUsage(new KeyPurposeId[] {id_kp_clientAuth, id_kp_emailProtection}));
  }

  @SneakyThrows
  private static Extension signing() {
    return Extension.create(keyUsage, true, new KeyUsage(digitalSignature));
  }

  @SneakyThrows
  private static X509Certificate buildCertificate(
      X500Name subjectDN, String issuerDn, Extension... extensions) {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    BigInteger serialNumber = new BigInteger(64, new SecureRandom());

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
    for (Extension extension : extensions) {
      certGen.addExtension(extension);
    }

    return new JcaX509CertificateConverter()
        .setProvider(new BouncyCastleProvider())
        .getCertificate(certGen.build(sigGen));
  }
}
