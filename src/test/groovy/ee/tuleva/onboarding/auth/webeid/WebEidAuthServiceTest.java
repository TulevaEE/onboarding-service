package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.OLD_ID_CARD;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import eu.webeid.security.authtoken.WebEidAuthToken;
import eu.webeid.security.challenge.ChallengeNonce;
import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.AuthTokenException;
import eu.webeid.security.exceptions.AuthTokenParseException;
import eu.webeid.security.exceptions.ChallengeNonceExpiredException;
import eu.webeid.security.validator.AuthTokenValidator;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebEidAuthServiceTest {

  @Mock ChallengeNonceGenerator challengeNonceGenerator;
  @Mock ChallengeNonceStore challengeNonceStore;
  @Mock AuthTokenValidator authTokenValidator;

  @InjectMocks WebEidAuthService webEidAuthService;

  @Test
  void generatesChallengeNonce() {
    var expectedNonce = new ChallengeNonce("test-nonce-base64", ZonedDateTime.now().plusMinutes(5));
    when(challengeNonceGenerator.generateAndStoreNonce()).thenReturn(expectedNonce);

    var result = webEidAuthService.generateChallenge();

    assertThat(result).isEqualTo("test-nonce-base64");
  }

  @Test
  void validatesAuthTokenAndReturnsIdCardSession() throws Exception {
    var nonce = new ChallengeNonce("valid-nonce", ZonedDateTime.now().plusMinutes(5));
    var authToken = createSampleAuthToken();
    var certificate = createTestCertificate();
    when(challengeNonceStore.getAndRemove()).thenReturn(nonce);
    when(authTokenValidator.validate(eq(authToken), eq("valid-nonce"))).thenReturn(certificate);

    var result = webEidAuthService.authenticate(authToken);

    assertThat(result.getFirstName()).isEqualTo(TEST_FIRST_NAME);
    assertThat(result.getLastName()).isEqualTo(TEST_LAST_NAME);
    assertThat(result.getPersonalCode()).isEqualTo(TEST_PERSONAL_CODE);
    assertThat(result.getDocumentType()).isEqualTo(OLD_ID_CARD);
  }

  @Test
  void throwsExceptionWhenNonceExpired() throws AuthTokenException {
    var authToken = createSampleAuthToken();
    when(challengeNonceStore.getAndRemove()).thenThrow(new ChallengeNonceExpiredException());

    assertThatThrownBy(() -> webEidAuthService.authenticate(authToken))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void throwsExceptionWhenTokenValidationFails() throws Exception {
    var nonce = new ChallengeNonce("valid-nonce", ZonedDateTime.now().plusMinutes(5));
    var authToken = createSampleAuthToken();
    when(challengeNonceStore.getAndRemove()).thenReturn(nonce);
    when(authTokenValidator.validate(eq(authToken), any()))
        .thenThrow(new AuthTokenParseException("Validation failed"));

    assertThatThrownBy(() -> webEidAuthService.authenticate(authToken))
        .isInstanceOf(WebEidAuthException.class);
  }

  private WebEidAuthToken createSampleAuthToken() {
    var token = new WebEidAuthToken();
    token.setFormat("web-eid:1");
    token.setUnverifiedCertificate("test-cert");
    token.setAlgorithm("ES384");
    token.setSignature("test-signature");
    return token;
  }
}
