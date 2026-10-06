package ee.tuleva.onboarding.signature;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

class SigningCertificatesTest {

  private final X509Certificate certificate =
      WebEidCertificateFixture.certificate("TEST", "USER", "38888888888", ESTONIAN_CITIZEN_ID_CARD);

  @Test
  void aCertificateBelongsToThePersonItsSubjectNames() throws CertificateEncodingException {
    assertThat(SigningCertificates.belongsTo(certificate, "38888888888")).isTrue();
  }

  @Test
  void aCertificateDoesNotBelongToAnyoneElse() throws CertificateEncodingException {
    assertThat(SigningCertificates.belongsTo(certificate, "38812121215")).isFalse();
  }

  @Test
  void aCertificateWithoutAPersonalCodeBelongsToNobody() throws CertificateEncodingException {
    X509Certificate withoutPersonalCode =
        WebEidCertificateFixture.certificateWithSubjectDn("C=EE, SURNAME=USER, GIVENNAME=TEST");

    assertThat(SigningCertificates.belongsTo(withoutPersonalCode, "38888888888")).isFalse();
  }
}
