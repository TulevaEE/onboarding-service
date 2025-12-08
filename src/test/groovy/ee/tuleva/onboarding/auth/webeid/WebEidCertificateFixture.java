package ee.tuleva.onboarding.auth.webeid;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
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

  public static final String TEST_PERSONAL_CODE = "38001085718";
  public static final String TEST_FIRST_NAME = "JAAK-KRISTJAN";
  public static final String TEST_LAST_NAME = "JÕEORG";

  @SneakyThrows
  public static X509Certificate createTestCertificate() {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    BigInteger serialNumber = new BigInteger(64, new SecureRandom());

    X500Name subjectDN =
        new X500Name(
            "C=EE, "
                + "O=ESTEID, "
                + "OU=AUTHENTICATION, "
                + "CN=\""
                + TEST_LAST_NAME
                + ","
                + TEST_FIRST_NAME
                + ","
                + TEST_PERSONAL_CODE
                + "\", "
                + "SURNAME="
                + TEST_LAST_NAME
                + ", "
                + "GIVENNAME="
                + TEST_FIRST_NAME
                + ", "
                + "SERIALNUMBER=PNOEE-"
                + TEST_PERSONAL_CODE);

    X500Name issuer =
        new X500Name("C=EE, O=SK ID Solutions AS, OID.2.5.4.97=NTREE-10747013, CN=ESTEID2018");

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

    return new JcaX509CertificateConverter()
        .setProvider(new BouncyCastleProvider())
        .getCertificate(certGen.build(sigGen));
  }
}
