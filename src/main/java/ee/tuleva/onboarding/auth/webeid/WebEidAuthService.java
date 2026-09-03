package ee.tuleva.onboarding.auth.webeid;

import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.idcard.IdDocumentTypeExtractor;
import eu.webeid.security.authtoken.WebEidAuthToken;
import eu.webeid.security.certificate.CertificateData;
import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.AuthTokenException;
import eu.webeid.security.exceptions.AuthTokenSignatureValidationException;
import eu.webeid.security.exceptions.CertificateNotTrustedException;
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

  private static final String ESTONIAN_PERSONAL_CODE_PREFIX = "PNOEE-";

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
    } catch (AuthTokenSignatureValidationException | CertificateNotTrustedException e) {
      log.error("Web eID configuration error", e);
      throw new WebEidConfigurationException("Web eID configuration error", e);
    } catch (AuthTokenException e) {
      log.info("Web eID token validation failed: {}", e.getMessage());
      throw new WebEidAuthException("Web eID token validation failed", e);
    }
  }

  private IdCardSession createSession(X509Certificate certificate) {
    try {
      var firstName =
          CertificateData.getSubjectGivenName(certificate)
              .orElseThrow(() -> new WebEidAuthException("Missing given name in certificate"));
      var lastName =
          CertificateData.getSubjectSurname(certificate)
              .orElseThrow(() -> new WebEidAuthException("Missing surname in certificate"));
      var serialNumber =
          CertificateData.getSubjectIdCode(certificate)
              .orElseThrow(() -> new WebEidAuthException("Missing personal code in certificate"));
      var personalCode = extractPersonalCode(serialNumber);

      var documentType = documentTypeExtractor.extract(certificate);
      documentTypeExtractor.checkClientAuthentication(certificate);
      documentTypeExtractor.checkIssuer(certificate);
      documentTypeExtractor.checkCountry(certificate);

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
    if (!serialNumber.startsWith(ESTONIAN_PERSONAL_CODE_PREFIX)) {
      throw new WebEidAuthException("Personal code in certificate is not Estonian");
    }
    return serialNumber.substring(ESTONIAN_PERSONAL_CODE_PREFIX.length());
  }
}
