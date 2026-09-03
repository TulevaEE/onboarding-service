package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificate;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithIssuer;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithSubjectDn;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithoutClientAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.idcard.IdDocumentTypeExtractor;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownCountryException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException;
import ee.tuleva.onboarding.auth.idcard.exception.UnsupportedDocumentTypeException;
import ee.tuleva.onboarding.auth.idcard.normalizer.ProductionCertificateNormalizer;
import eu.webeid.security.authtoken.WebEidAuthToken;
import eu.webeid.security.challenge.ChallengeNonce;
import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.AuthTokenException;
import eu.webeid.security.exceptions.AuthTokenParseException;
import eu.webeid.security.exceptions.ChallengeNonceExpiredException;
import eu.webeid.security.validator.AuthTokenValidator;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebEidAuthServiceTest {

  private static final String TEST_PERSONAL_CODE = "38001085718";
  private static final String TEST_FIRST_NAME = "MARI-LIIS";
  private static final String TEST_LAST_NAME = "MÄNNIK";

  @Mock private AuthTokenValidator authTokenValidator;
  @Mock private ChallengeNonceStore challengeNonceStore;

  private WebEidAuthService service;

  @BeforeEach
  void setUp() {
    var normalizer = new ProductionCertificateNormalizer();
    service =
        new WebEidAuthService(
            null,
            challengeNonceStore,
            authTokenValidator,
            new IdDocumentTypeExtractor(List.of(), normalizer));
  }

  @Test
  void generateChallenge_returnsNonceFromGenerator() {
    var expectedNonce = new ChallengeNonce("test-nonce-base64", ZonedDateTime.now().plusMinutes(5));
    var generator = mock(ChallengeNonceGenerator.class);
    when(generator.generateAndStoreNonce()).thenReturn(expectedNonce);
    var normalizer = new ProductionCertificateNormalizer();
    var serviceWithGenerator =
        new WebEidAuthService(
            generator,
            challengeNonceStore,
            authTokenValidator,
            new IdDocumentTypeExtractor(List.of(), normalizer));

    var result = serviceWithGenerator.generateChallenge();

    assertThat(result).isEqualTo("test-nonce-base64");
  }

  @Test
  void authenticate_returnsSessionWithCorrectUserDataAndDocumentType() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificate(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PERSONAL_CODE, ESTONIAN_CITIZEN_ID_CARD));

    var session = service.authenticate(new WebEidAuthToken());

    var expected =
        IdCardSession.builder()
            .firstName(TEST_FIRST_NAME)
            .lastName(TEST_LAST_NAME)
            .personalCode(TEST_PERSONAL_CODE)
            .documentType(ESTONIAN_CITIZEN_ID_CARD)
            .build();
    assertThat(session).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(
      value = IdDocumentType.class,
      names = {
        "ESTONIAN_CITIZEN_ID_CARD",
        "DIGITAL_ID_CARD",
        "LONG_TERM_RESIDENCE_CARD",
        "TEMPORARY_RESIDENCE_CARD",
        "DIPLOMATIC_ID_CARD"
      })
  void authenticate_extractsDocumentTypeFromCertificatePolicyOid(IdDocumentType documentType)
      throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(certificate(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PERSONAL_CODE, documentType));

    var session = service.authenticate(new WebEidAuthToken());

    assertThat(session.getDocumentType()).isEqualTo(documentType);
  }

  @Test
  void authenticate_failsWhenCertificateHasInvalidIssuer() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithIssuer(
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PERSONAL_CODE,
                "CN=FAKE-ISSUER, O=Fake CA, C=XX"));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(UnknownIssuerException.class);
  }

  @Test
  void authenticate_failsWhenCertificateLacksClientAuthenticationKeyUsage()
      throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithoutClientAuth(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PERSONAL_CODE));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(UnknownExtendedKeyUsageException.class);
  }

  @Test
  void authenticate_failsWhenNonceExpired() throws AuthTokenException {
    when(challengeNonceStore.getAndRemove()).thenThrow(new ChallengeNonceExpiredException());

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_failsWhenTokenValidationFails() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenThrow(new AuthTokenParseException("Validation failed"));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_failsWhenCertificateHasNoGivenName() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithSubjectDn(
                "C=EE, O=ESTEID, OU=AUTHENTICATION, SURNAME=MÄNNIK, SERIALNUMBER=PNOEE-"
                    + TEST_PERSONAL_CODE));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_failsWhenCertificateHasNoSurname() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithSubjectDn(
                "C=EE, O=ESTEID, OU=AUTHENTICATION, GIVENNAME=MARI-LIIS, SERIALNUMBER=PNOEE-"
                    + TEST_PERSONAL_CODE));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_failsWhenCertificateHasNoPersonalCode() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithSubjectDn(
                "C=EE, O=ESTEID, OU=AUTHENTICATION, GIVENNAME=MARI-LIIS, SURNAME=MÄNNIK"));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_failsWhenThePersonalCodeIsNotEstonian() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithSubjectDn(
                "C=EE, O=ESTEID, OU=AUTHENTICATION, CN=\"DOE,JOHN,PASJP-123456789\", "
                    + "SURNAME=DOE, GIVENNAME=JOHN, SERIALNUMBER=PASJP-123456789"));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void authenticate_refusesAnEResidentDigitalId() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificate(
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PERSONAL_CODE,
                IdDocumentType.E_RESIDENT_DIGITAL_ID_CARD));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(UnsupportedDocumentTypeException.class);
  }

  @Test
  void authenticate_refusesAnEuCitizenIdCard() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificate(
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PERSONAL_CODE,
                IdDocumentType.EUROPEAN_CITIZEN_ID_CARD));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(UnsupportedDocumentTypeException.class);
  }

  @Test
  void authenticate_failsWhenTheCertificateCountryIsNotEstonia() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any()))
        .thenReturn(
            certificateWithSubjectDn(
                "C=LT, O=ESTEID, OU=AUTHENTICATION, CN=\"DOE,JOHN,38888888888\", "
                    + "SURNAME=DOE, GIVENNAME=JOHN, SERIALNUMBER=PNOEE-38888888888"));

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(UnknownCountryException.class);
  }

  private void setupNonceStore() throws AuthTokenException {
    var nonce = new ChallengeNonce("test-nonce", ZonedDateTime.now().plusMinutes(5));
    when(challengeNonceStore.getAndRemove()).thenReturn(nonce);
  }
}
