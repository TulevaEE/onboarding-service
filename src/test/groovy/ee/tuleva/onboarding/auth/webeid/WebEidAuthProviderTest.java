package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.GrantType.*;
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.OLD_ID_CARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WebEidAuthProviderTest {

  @Mock WebEidAuthService webEidAuthService;
  @Mock GenericSessionStore sessionStore;
  @Mock PrincipalService principalService;

  JsonMapper objectMapper = JsonMapper.builder().build();
  WebEidAuthProvider webEidAuthProvider;

  @BeforeEach
  void setUp() {
    webEidAuthProvider =
        new WebEidAuthProvider(webEidAuthService, sessionStore, principalService, objectMapper);
  }

  @Test
  void supportsIdCard() {
    assertThat(webEidAuthProvider.supports(ID_CARD)).isTrue();
    assertThat(webEidAuthProvider.supports(SMART_ID)).isFalse();
    assertThat(webEidAuthProvider.supports(MOBILE_ID)).isFalse();
  }

  @Test
  void authenticatesWithValidAuthToken() {
    String authTokenJson =
        """
        {"format":"web-eid:1","unverifiedCertificate":"test","algorithm":"ES384","signature":"sig"}
        """;
    var idCardSession =
        IdCardSession.builder()
            .firstName("John")
            .lastName("Doe")
            .personalCode("38001085718")
            .documentType(OLD_ID_CARD)
            .build();
    var expectedPerson = sampleAuthenticatedPersonAndMember().build();

    when(webEidAuthService.authenticate(any())).thenReturn(idCardSession);
    when(principalService.getFrom(any(), any())).thenReturn(expectedPerson);

    AuthenticatedPerson result = webEidAuthProvider.authenticate(authTokenJson);

    assertThat(result).isEqualTo(expectedPerson);
  }

  @Test
  void returnsNullWhenAuthTokenIsNull() {
    assertThat(webEidAuthProvider.authenticate(null)).isNull();
  }

  @Test
  void throwsExceptionWhenAuthTokenIsInvalidJson() {
    assertThatThrownBy(() -> webEidAuthProvider.authenticate("invalid-json"))
        .isInstanceOf(WebEidAuthException.class);
  }

  @Test
  void savesIdCardSessionToSessionStore() {
    String authTokenJson =
        """
        {"format":"web-eid:1","unverifiedCertificate":"test","algorithm":"ES384","signature":"sig"}
        """;
    var idCardSession =
        IdCardSession.builder()
            .firstName("John")
            .lastName("Doe")
            .personalCode("38001085718")
            .documentType(OLD_ID_CARD)
            .build();
    var expectedPerson = sampleAuthenticatedPersonAndMember().build();

    when(webEidAuthService.authenticate(any())).thenReturn(idCardSession);
    when(principalService.getFrom(any(), any())).thenReturn(expectedPerson);

    webEidAuthProvider.authenticate(authTokenJson);

    verify(sessionStore).save(idCardSession);
  }
}
