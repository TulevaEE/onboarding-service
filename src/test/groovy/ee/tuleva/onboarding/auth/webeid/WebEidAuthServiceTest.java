package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificate;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithIssuer;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithSubjectDn;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithoutClientAuth;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithoutPolicies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.idcard.IdDocumentTypeExtractor;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException;
import ee.tuleva.onboarding.auth.idcard.normalizer.ProductionCertificateNormalizer;
import eu.webeid.security.authtoken.WebEidAuthToken;
import eu.webeid.security.challenge.ChallengeNonce;
import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.AuthTokenException;
import eu.webeid.security.exceptions.AuthTokenParseException;
import eu.webeid.security.exceptions.ChallengeNonceExpiredException;
import eu.webeid.security.validator.AuthTokenValidator;
import eu.webeid.security.validator.AuthTokenValidatorBuilder;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        "E_RESIDENT_DIGITAL_ID_CARD",
        "EUROPEAN_CITIZEN_ID_CARD",
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
  void extractPersonalCode_returnsSerialNumberUnchangedWhenNotEstonian() {
    var foreignSerialNumber = "PASJP-123456789";

    String personalCode =
        ReflectionTestUtils.invokeMethod(service, "extractPersonalCode", foreignSerialNumber);

    assertThat(personalCode).isEqualTo(foreignSerialNumber);
  }

  @Test
  void authenticate_failsWhenTokenValidatorThrowsUnexpectedly() throws AuthTokenException {
    setupNonceStore();
    when(authTokenValidator.validate(any(), any())).thenThrow(new IllegalStateException());

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(WebEidAuthException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void authenticate_propagatesNonceStoreFailures() throws AuthTokenException {
    when(challengeNonceStore.getAndRemove()).thenThrow(new IllegalStateException());

    assertThatThrownBy(() -> service.authenticate(new WebEidAuthToken()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void authenticate_failsWhenCertificateHasNoPoliciesExtension() throws Exception {
    setupNonceStore();
    var certificate =
        certificateWithoutPolicies(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PERSONAL_CODE);
    var serviceWithRealValidator =
        new WebEidAuthService(
            null,
            challengeNonceStore,
            new AuthTokenValidatorBuilder()
                .withSiteOrigin(URI.create("https://onboarding-service.tuleva.ee"))
                .withTrustedCertificateAuthorities(certificate)
                .build(),
            new IdDocumentTypeExtractor(List.of(), new ProductionCertificateNormalizer()));

    assertThatThrownBy(() -> serviceWithRealValidator.authenticate(authToken(certificate)))
        .isInstanceOf(WebEidAuthException.class)
        .hasCauseInstanceOf(NullPointerException.class);
  }

  private static WebEidAuthToken authToken(X509Certificate certificate) throws Exception {
    var authToken = new WebEidAuthToken();
    authToken.setFormat("web-eid:1.0");
    authToken.setUnverifiedCertificate(
        Base64.getEncoder().encodeToString(certificate.getEncoded()));
    authToken.setAlgorithm("ES384");
    authToken.setSignature("signature");
    return authToken;
  }

  private void setupNonceStore() throws AuthTokenException {
    var nonce = new ChallengeNonce("test-nonce", ZonedDateTime.now().plusMinutes(5));
    when(challengeNonceStore.getAndRemove()).thenReturn(nonce);
  }
}
