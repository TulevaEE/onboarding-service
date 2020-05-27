package ee.tuleva.onboarding.auth.ocsp;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.xml.bind.DatatypeConverter;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

public class OCSPFixture {
  public static String sampleExampleServer = "http://aia.sk.ee/esteid2015";
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OCSPFixture.class);

  public static X509Certificate generateCertificate(
      String dn, int days, String algorithm, String urlCA, String urlOCSP) throws Exception {

    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      PublicKey publicKey = keyPair.getPublic();
      PrivateKey privateKey = keyPair.getPrivate();

      BigInteger serialNumber = new BigInteger(64, new SecureRandom());

      X500Name subjectDN = new X500Name("O=Org,CN=original,L=Tallinn,C=EE,OU=Unit");

      RDN[] rdns = subjectDN.getRDNs();
      for (int i = 0; i < rdns.length; i++) {
        AttributeTypeAndValue[] atts = rdns[i].getTypesAndValues();
        for (int j = 0; j < atts.length; j++) {
          if (atts[j].getType().equals(BCStyle.CN)) {
            atts[j] = new AttributeTypeAndValue(BCStyle.CN, new DERUTF8String(dn));
            rdns[i] = new RDN(atts);
          }
        }
      }

      subjectDN = new X500Name(rdns);

      Date from = new Date();
      Date to = new Date(from.getTime() + days * 86400000l);

      SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());

      AlgorithmIdentifier sigAlgId =
          new DefaultSignatureAlgorithmIdentifierFinder().find(algorithm);
      AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
      AsymmetricKeyParameter privateKeyAsymKeyParam =
          PrivateKeyFactory.createKey(privateKey.getEncoded());

      ContentSigner sigGen =
          new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);

      X509v3CertificateBuilder certGen =
          new X509v3CertificateBuilder(subjectDN, serialNumber, from, to, subjectDN, subPubKeyInfo);
      ASN1EncodableVector aia_ASN = new ASN1EncodableVector();

      if (urlCA != null) {
        AccessDescription caIssuers =
            new AccessDescription(
                AccessDescription.id_ad_caIssuers,
                new GeneralName(GeneralName.uniformResourceIdentifier, new DERIA5String(urlCA)));

        aia_ASN.add(caIssuers);
      }

      if (urlOCSP != null) {
        AccessDescription ocsp =
            new AccessDescription(
                AccessDescription.id_ad_ocsp,
                new GeneralName(GeneralName.uniformResourceIdentifier, new DERIA5String(urlOCSP)));
        aia_ASN.add(ocsp);
      }

      if (urlOCSP != null || urlCA != null) {
        certGen.addExtension(Extension.authorityInfoAccess, false, new DERSequence(aia_ASN));
      }

      return new JcaX509CertificateConverter()
          .setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
          .getCertificate(certGen.build(sigGen));

    } catch (CertificateException ce) {
      throw ce;
    } catch (Exception e) {
      throw new CertificateException(e);
    }
  }

  public static String certToString(X509Certificate cert) {
    StringWriter sw = new StringWriter();
    try {
      sw.write("-----BEGIN CERTIFICATE-----\n");
      sw.write(
          DatatypeConverter.printBase64Binary(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
      sw.write("\n-----END CERTIFICATE-----\n");
    } catch (CertificateEncodingException e) {
      log.warn("Unable to encode certificate", e);
    }
    return sw.toString();
  }
}
