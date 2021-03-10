package ee.tuleva.onboarding.auth.ocsp;

import static ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code.INVALID_INPUT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

@Component
public class OCSPUtils {
  private static final String AUTH_INFO_ACCESS = Extension.authorityInfoAccess.getId();

  public URI getIssuerCertificateURI(X509Certificate certificate) {
    return getURIFromCertificate(certificate, AccessDescription.id_ad_caIssuers);
  }

  public URI getResponderURI(X509Certificate certificate) {
    return getURIFromCertificate(certificate, AccessDescription.id_ad_ocsp);
  }

  private URI getURIFromCertificate(
      X509Certificate certificate, ASN1Primitive accessDescriptionToFind) {
    AuthorityInformationAccess authorityInformationAccess = null;
    try {
      ASN1Primitive extensionValue = getExtensionValue(certificate, AUTH_INFO_ACCESS);
      if (extensionValue != null) {
        authorityInformationAccess = AuthorityInformationAccess.getInstance(extensionValue);
      }
      List<String> urls =
          findUrlsFromAccessDescriptions(authorityInformationAccess, accessDescriptionToFind);
      return new URI(urls.get(0));
    } catch (Exception e) {
      throw new AuthenticationException(INVALID_INPUT, "Unable to read certificate", e);
    }
  }

  private static ASN1Primitive getExtensionValue(X509Extension x509Extension, String extensionId)
      throws Exception {
    byte[] extensionValueBytes = x509Extension.getExtensionValue(extensionId);
    if (extensionValueBytes == null) {
      return null;
    }

    ASN1InputStream extensionValueInputStream = new ASN1InputStream(extensionValueBytes);
    ASN1OctetString extensionValueOctetString =
        (ASN1OctetString) extensionValueInputStream.readObject();

    extensionValueInputStream = new ASN1InputStream(extensionValueOctetString.getOctets());
    return extensionValueInputStream.readObject();
  }

  private List<String> findUrlsFromAccessDescriptions(
      AuthorityInformationAccess authInfoAccess, ASN1Primitive accessDescriptionToFind) {
    List<String> urls = new ArrayList<>();

    if (authInfoAccess != null) {
      List<AccessDescription> accessDescriptions =
          new ArrayList<>(Arrays.asList(authInfoAccess.getAccessDescriptions()));
      accessDescriptions.forEach(
          accessDescription -> {
            if (accessDescription.getAccessMethod().equals(accessDescriptionToFind)) {
              GeneralName name = accessDescription.getAccessLocation();
              if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
                String url = ((DERIA5String) name.getName()).getString();
                urls.add(url);
              }
            }
          });
    }

    return urls;
  }

  public X509Certificate getX509Certificate(String certificate) {
    byte[] certificateBytes = certificate.getBytes(UTF_8);
    return generateX09Certificate(certificateBytes);
  }

  @SneakyThrows
  public X509Certificate decodeX09Certificate(String hexCertificate) {
    byte[] decodedCertificate = Hex.decodeHex(hexCertificate);
    return generateX09Certificate(decodedCertificate);
  }

  private X509Certificate generateX09Certificate(byte[] certificate) {
    try {
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));
    } catch (CertificateException e) {
      throw new AuthenticationException(INVALID_INPUT, "Unable to generate certificate", e);
    }
  }

  public OCSPRequest generateOCSPRequest(
      X509Certificate certificate, String certificationAuthority, String responderUrl) {

    try {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      InputStream in = new ByteArrayInputStream(certificationAuthority.getBytes(UTF_8));
      X509Certificate issuerCert = (X509Certificate) certFactory.generateCertificate(in);

      OCSPReq ocspReq = getCreateOCSPReq(issuerCert, certificate);
      return new OCSPRequest(responderUrl, certificate, ocspReq);
    } catch (CertificateException e) {
      throw new AuthenticationException(
          AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Uncaught error", e);
    }
  }

  public OCSPReq getCreateOCSPReq(X509Certificate issuerCert, X509Certificate certificate) {
    try {
      JcaDigestCalculatorProviderBuilder digestCalculatorProviderBuilder =
          new JcaDigestCalculatorProviderBuilder();
      DigestCalculatorProvider digestCalculatorProvider = digestCalculatorProviderBuilder.build();
      DigestCalculator digestCalculator = digestCalculatorProvider.get(CertificateID.HASH_SHA1);

      CertificateID id =
          new CertificateID(
              digestCalculator,
              new JcaX509CertificateHolder(issuerCert),
              certificate.getSerialNumber());

      OCSPReqBuilder ocspGen = new OCSPReqBuilder();
      ocspGen.addRequest(id);
      return ocspGen.build();
    } catch (OperatorCreationException | CertificateEncodingException | OCSPException e) {
      throw new AuthenticationException(
          AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Uncaught error", e);
    }
  }
}
