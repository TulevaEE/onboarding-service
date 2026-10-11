package ee.tuleva.onboarding.signature;

import ee.tuleva.onboarding.personalcode.PersonalCode;
import eu.webeid.security.certificate.CertificateData;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public final class SigningCertificates {

  private SigningCertificates() {}

  public static boolean belongsTo(X509Certificate certificate, String personalCode)
      throws CertificateEncodingException {
    return CertificateData.getSubjectIdCode(certificate)
        .flatMap(PersonalCode::fromEstonianSubjectIdCode)
        .filter(personalCode::equals)
        .isPresent();
  }
}
