package ee.tuleva.onboarding.auth.ocsp;

import static ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code.INVALID_INPUT;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import java.util.Vector;
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
import org.bouncycastle.jce.provider.AnnotatedException;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

@Component
public class OCSPUtils {
  private static final String AUTH_INFO_ACCESS = Extension.authorityInfoAccess.getId();

  public URI getIssuerCertificateURI(X509Certificate certificate) {
    AuthorityInformationAccess authInfoAcc = null;
    try {
      ASN1Primitive auth_info_acc = getExtensionValue(certificate, AUTH_INFO_ACCESS);
      if (auth_info_acc != null) {
        authInfoAcc = AuthorityInformationAccess.getInstance(auth_info_acc);
      }
      Vector<String> ocspUrls = findUrls(authInfoAcc, AccessDescription.id_ad_caIssuers);
      return new URI(ocspUrls.firstElement());
    } catch (AnnotatedException | URISyntaxException | NoSuchElementException e) {
      throw new AuthenticationException(INVALID_INPUT, "Unable to read certificate", e);
    }
  }

  public URI getResponderURI(X509Certificate certificate) {
    AuthorityInformationAccess authInfoAcc = null;
    try {
      ASN1Primitive auth_info_acc = getExtensionValue(certificate, AUTH_INFO_ACCESS);
      if (auth_info_acc != null) {
        authInfoAcc = AuthorityInformationAccess.getInstance(auth_info_acc);
      }
      Vector<String> ocspUrls = findUrls(authInfoAcc, AccessDescription.id_ad_ocsp);
      return new URI(ocspUrls.firstElement());
    } catch (AnnotatedException | URISyntaxException | NoSuchElementException e) {
      throw new AuthenticationException(INVALID_INPUT, "Unable to read certificate", e);
    }
  }

  protected static ASN1Primitive getExtensionValue(java.security.cert.X509Extension ext, String oid)
      throws AnnotatedException {
    byte[] bytes = ext.getExtensionValue(oid);
    if (bytes == null) {
      return null;
    }

    return getObject(oid, bytes);
  }

  private static ASN1Primitive getObject(String oid, byte[] ext) throws AnnotatedException {
    try {
      ASN1InputStream aIn = new ASN1InputStream(ext);
      ASN1OctetString octs = (ASN1OctetString) aIn.readObject();

      aIn = new ASN1InputStream(octs.getOctets());
      return aIn.readObject();
    } catch (Exception e) {
      throw new AnnotatedException("exception processing extension " + oid, e);
    }
  }

  protected Vector<String> findUrls(
      AuthorityInformationAccess authInfoAccess, ASN1Primitive accessDescription) {
    Vector<String> urls = new Vector();

    if (authInfoAccess != null) {
      AccessDescription[] ads = authInfoAccess.getAccessDescriptions();
      for (int i = 0; i < ads.length; i++) {
        if (ads[i].getAccessMethod().equals(accessDescription)) {
          GeneralName name = ads[i].getAccessLocation();
          if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
            String url = ((DERIA5String) name.getName()).getString();
            urls.add(url);
          }
        }
      }
    }

    return urls;
  }

  public X509Certificate getX509Certificate(String certificate) {
    CertificateFactory cf = null;
    try {
      cf = CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          cf.generateCertificate(new ByteArrayInputStream(certificate.getBytes("UTF-8")));
    } catch (CertificateException | UnsupportedEncodingException e) {
      throw new AuthenticationException(INVALID_INPUT, "Unable to read certificate", e);
    }
  }

  public OCSPRequest generateOCSPRequest(
      X509Certificate certificate, String certificationAuthority, String responderUrl) {

    try {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      InputStream in = new ByteArrayInputStream(certificationAuthority.getBytes("UTF-8"));
      X509Certificate issuerCert = (X509Certificate) certFactory.generateCertificate(in);

      OCSPReq ocspReq = getCreateOCSPReq(issuerCert, certificate);
      return new OCSPRequest(responderUrl, certificate, ocspReq);
    } catch (CertificateException | UnsupportedEncodingException e) {
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
