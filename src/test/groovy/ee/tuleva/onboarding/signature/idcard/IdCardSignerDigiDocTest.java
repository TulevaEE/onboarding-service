package ee.tuleva.onboarding.signature.idcard;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static java.util.Base64.getEncoder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.digidoc4j.Configuration.Mode.TEST;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import org.digidoc4j.Configuration;
import org.junit.jupiter.api.Test;

class IdCardSignerDigiDocTest {

  private static final String PERSONAL_CODE = "38888888888";

  private final IdCardSigner idCardSigner =
      new IdCardSigner(new DigiDocFacade(new Configuration(TEST)));
  private final SignableEntity entity = new SignableEntity("Mandate", 1L);

  @Test
  void rejectsAWellFormedSignatureThatDoesNotVerifyAgainstTheSigningCertificate()
      throws CertificateEncodingException {
    var certificate =
        WebEidCertificateFixture.certificate(
            "TEST", "USER", PERSONAL_CODE, ESTONIAN_CITIZEN_ID_CARD);
    var session =
        idCardSigner.startSign(
            entity,
            List.of(new SignatureFile("file.txt", "text/plain", "content".getBytes())),
            getEncoder().encodeToString(certificate.getEncoded()),
            List.of("SHA-256"),
            PERSONAL_CODE);
    var signatureOverSomethingElse = getEncoder().encodeToString(new byte[256]);

    assertThatThrownBy(
            () -> idCardSigner.getSignedFile(session, entity, signatureOverSomethingElse))
        .isInstanceOf(InvalidSignatureException.class);
  }
}
