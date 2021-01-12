package ee.tuleva.onboarding.auth.ocsp;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import static ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code.*;
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.GOOD;

@Service
@Slf4j
@AllArgsConstructor
public class OCSPAuthService {
  private final OCSPUtils ocspUtils;
  private final OCSPService service;

  public CheckCertificateResponse checkCertificate(String certificate) {
    X509Certificate cert = ocspUtils.getX509Certificate(certificate);
    return checkCertificate(cert);
  }

  public CheckCertificateResponse checkCertificate(X509Certificate certificate) {

    URI issuerURI = ocspUtils.getIssuerCertificateURI(certificate);
    URI responderURI = ocspUtils.getResponderURI(certificate);

    String certificationAuthority = service.getIssuerCertificate(issuerURI.toString());

    OCSPRequest request =
      ocspUtils.generateOCSPRequest(certificate, certificationAuthority, responderURI.toString());

    OCSPResponseType result = service.checkCertificate(request);
    validateGoodResult(result);

    return getCreateCertificateResponse(certificate);
  }

  @NotNull
  private CheckCertificateResponse getCreateCertificateResponse(X509Certificate cert) {
    X500Name x500name;
    try {
      x500name = new JcaX509CertificateHolder(cert).getSubject();
    } catch (CertificateEncodingException e) {
      throw new AuthenticationException(INVALID_INPUT);
    }
    RDN cn = x500name.getRDNs(BCStyle.CN)[0];
    String subject = IETFUtils.valueToString(cn.getFirst().getValue());
    String[] parts = subject.split("\\\\,");

    if (parts.length != 3) {
      log.warn("Subject line did not have 3 separated parts, so parsing failed");
      throw new AuthenticationException(USER_CERTIFICATE_MISSING);
    }

    return new CheckCertificateResponse(parts[1], parts[0], parts[2]);
  }

  private void validateGoodResult(OCSPResponseType result) {
    if (!GOOD.equals(result)) throw new AuthenticationException(valueOf(result.toString()));
  }
}
