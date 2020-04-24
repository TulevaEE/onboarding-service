package ee.tuleva.onboarding.auth.ocsp;

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
import sun.security.x509.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code.INVALID_INPUT;

@Component
public class OCSPUtils {

  public URI getIssuerCertificateURI(X509Certificate certificate) {
    AuthorityInfoAccessExtension extension =
        ((X509CertImpl) certificate).getAuthorityInfoAccessExtension();
    List<AccessDescription> accessDescriptions = extension.getAccessDescriptions();
    AccessDescription result =
        accessDescriptions.stream()
            .filter(
                accessDescription ->
                    accessDescription.getAccessMethod().equals(AccessDescription.Ad_CAISSUERS_Id))
            .findFirst()
            .orElseThrow(
                () -> new AuthenticationException(INVALID_INPUT, "Issuer missing in certificate"));

    GeneralName generalName = result.getAccessLocation();
    URIName uri = (URIName) generalName.getName();
    return uri.getURI();
  }

  public URI getResponderURI(X509Certificate certificate) {
    AuthorityInfoAccessExtension extension =
        ((X509CertImpl) certificate).getAuthorityInfoAccessExtension();
    List<AccessDescription> accessDescriptions = extension.getAccessDescriptions();
    AccessDescription result =
        accessDescriptions.stream()
            .filter(
                accessDescription ->
                    accessDescription.getAccessMethod().equals(AccessDescription.Ad_OCSP_Id))
            .findFirst()
            .orElseThrow(
                () ->
                    new AuthenticationException(
                        INVALID_INPUT, "Responder OCSP URI missing in certificate"));
    GeneralName generalName = result.getAccessLocation();
    URIName uri = (URIName) generalName.getName();
    return uri.getURI();
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
