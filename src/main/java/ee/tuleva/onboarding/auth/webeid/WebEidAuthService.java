package ee.tuleva.onboarding.auth.webeid;

import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.idcard.IdDocumentTypeExtractor;
import eu.webeid.security.authtoken.WebEidAuthToken;
import eu.webeid.security.certificate.CertificateData;
import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.AuthTokenException;
import eu.webeid.security.exceptions.ChallengeNonceExpiredException;
import eu.webeid.security.validator.AuthTokenValidator;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebEidAuthService {

  private final ChallengeNonceGenerator challengeNonceGenerator;
  private final ChallengeNonceStore challengeNonceStore;
  private final AuthTokenValidator authTokenValidator;
  private final IdDocumentTypeExtractor documentTypeExtractor;

  public String generateChallenge() {
    return challengeNonceGenerator.generateAndStoreNonce().getBase64EncodedNonce();
  }

  public IdCardSession authenticate(WebEidAuthToken authToken) {
    try {
      log.info("Validating Web eID auth token");
      var nonce = challengeNonceStore.getAndRemove();
      X509Certificate certificate =
          authTokenValidator.validate(authToken, nonce.getBase64EncodedNonce());
      return createSession(certificate);
    } catch (ChallengeNonceExpiredException e) {
      log.error("Web eID challenge nonce expired or not found", e);
      throw new WebEidAuthException("Challenge nonce expired or not found", e);
    } catch (AuthTokenException e) {
      log.error("Web eID token validation failed", e);
      throw new WebEidAuthException("Web eID token validation failed", e);
    }
  }

  private IdCardSession createSession(X509Certificate certificate) {
    try {
      var firstName =
          CertificateData.getSubjectGivenName(certificate)
              .orElseThrow(
                  () -> new WebEidAuthException("Missing given name in certificate", null));
      var lastName =
          CertificateData.getSubjectSurname(certificate)
              .orElseThrow(() -> new WebEidAuthException("Missing surname in certificate", null));
      var serialNumber =
          CertificateData.getSubjectIdCode(certificate)
              .orElseThrow(
                  () -> new WebEidAuthException("Missing personal code in certificate", null));
      var personalCode = extractPersonalCode(serialNumber);

      var documentType = documentTypeExtractor.extract(certificate);
      documentTypeExtractor.checkClientAuthentication(certificate);
      documentTypeExtractor.checkIssuer(certificate);

      return IdCardSession.builder()
          .firstName(firstName)
          .lastName(lastName)
          .personalCode(personalCode)
          .documentType(documentType)
          .build();
    } catch (CertificateEncodingException e) {
      throw new WebEidAuthException("Failed to read certificate data", e);
    }
  }

  private String extractPersonalCode(String serialNumber) {
    if (serialNumber.startsWith("PNOEE-")) {
      return serialNumber.substring(6);
    }
    return serialNumber;
  }
}
