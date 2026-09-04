package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.IdSessionException;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MandateBatchSignatureServiceTest {

  @Mock private MandateBatchService mandateBatchService;

  @Mock private GenericSessionStore sessionStore;

  @Mock private LocaleService localeService;

  @Mock private SignatureService signService;
  @Mock private UserService userService;

  @InjectMocks private MandateBatchSignatureService mandateBatchSignatureService;

  @Nested
  @DisplayName("mobile id")
  class MobileIdTests {

    @Test
    @DisplayName("start mobile id signature returns the mobile ID challenge code")
    void startMobileIdSignatureReturnsChallengeCode() {
      var mandateBatchId = 1L;
      var phoneNumber = "+372 555 5555";
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();
      var user = sampleUser().build();
      var authenticatedPerson =
          authenticatedPersonFromUser(user).attributes(Map.of(PHONE_NUMBER, phoneNumber)).build();

      when(userService.getById(eq(authenticatedPerson.getUserId()))).thenReturn(Optional.of(user));
      when(mandateBatchService.getMandateBatchContentFiles(eq(mandateBatchId), eq(user)))
          .thenReturn(List.of());
      when(signService.startMobileIdSign(
              any(), eq(authenticatedPerson.getPersonalCode()), eq(phoneNumber)))
          .thenReturn(mockSession);

      var result =
          mandateBatchSignatureService.startMobileIdSignature(mandateBatchId, authenticatedPerson);

      assertThat(result.getChallengeCode()).isEqualTo("1234");
      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("get mobile id signature status returns the status and challenge code")
    void getMobileIdSignatureStatusReturnsStatusAndChallengeCode() {
      var mandateBatchId = 1L;
      var mockSession = MobileIdSignatureSession.builder().verificationCode("1234").build();
      var user = sampleAuthenticatedPersonAndMember().build();

      when(sessionStore.get(MobileIdSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeMobileSignature(
              any(), eq(mandateBatchId), any(MobileIdSignatureSession.class), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      var result = mandateBatchSignatureService.getMobileIdSignatureStatus(mandateBatchId, user);

      assertThat(result.getStatusCode()).isEqualTo(SIGNATURE);
      assertThat(result.getChallengeCode()).isEqualTo("1234");
    }

    @Test
    void earlyStatusPollWithoutVerificationCodeStillReturnsTheStatus() {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);

      when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeMobileSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq(Locale.ENGLISH)))
          .thenReturn(OUTSTANDING_TRANSACTION);

      var user = sampleAuthenticatedPersonAndMember().build();
      var result = mandateBatchSignatureService.getSmartIdSignatureStatus(mandateBatchId, user);

      assertThat(result.getStatusCode()).isEqualTo(OUTSTANDING_TRANSACTION);
      assertThat(result.getChallengeCode()).isNull();
    }
  }

  @Nested
  @DisplayName("smart id")
  class SmartIdTests {

    @Test
    @DisplayName("start smart id signature returns null challenge code")
    void startSmartIdSignatureReturnsNullChallengeCode() {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode(null);
      var user = sampleUser().build();
      var authenticatedPerson = authenticatedPersonFromUser(user).build();

      when(userService.getById(eq(authenticatedPerson.getUserId()))).thenReturn(Optional.of(user));
      when(mandateBatchService.getMandateBatchContentFiles(eq(mandateBatchId), eq(user)))
          .thenReturn(List.of());
      when(signService.startSmartIdSign(any(), eq(user.getPersonalCode()))).thenReturn(mockSession);

      var result =
          mandateBatchSignatureService.startSmartIdSignature(mandateBatchId, authenticatedPerson);

      assertThat(result.getChallengeCode()).isNull();
      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("get smart id signature status returns the status and challenge code")
    void getSmartIdSignatureStatusReturnsStatusAndChallengeCode() {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode("1234");

      when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeMobileSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      var user = sampleAuthenticatedPersonAndMember().build();
      var result = mandateBatchSignatureService.getSmartIdSignatureStatus(mandateBatchId, user);

      assertThat(result.getStatusCode()).isEqualTo(SIGNATURE);
      assertThat(result.getChallengeCode()).isEqualTo("1234");
    }
  }

  @Nested
  @DisplayName("id card")
  class IdCardTests {

    @Test
    void startIdCardSignatureReturnsTheHashToSignAndItsHashFunction() {
      var mandateBatchId = 1L;
      var certificate = "certificate";
      var startCommand = MandateFixture.sampleStartIdCardSignCommand(certificate);
      var mockSession =
          IdCardSignatureSession.builder().hashToSign("asdfg").hashFunction("SHA-256").build();
      var user = sampleUser().build();
      var authenticatedPerson = authenticatedPersonFromUser(user).build();

      when(userService.getById(eq(authenticatedPerson.getUserId()))).thenReturn(Optional.of(user));
      when(mandateBatchService.getMandateBatchContentFiles(eq(mandateBatchId), eq(user)))
          .thenReturn(List.of());
      when(signService.startIdCardSign(
              any(), eq(certificate), eq(List.of("SHA-256")), eq(user.getPersonalCode())))
          .thenReturn(mockSession);

      var result =
          mandateBatchSignatureService.startIdCardSign(
              mandateBatchId, authenticatedPerson, startCommand);

      assertThat(result).isEqualTo(new IdCardSignatureResponse("asdfg", "SHA-256"));
      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    void persistIdCardSignatureReturnsTheProcessingStatus() {
      var mandateBatchId = 1L;
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand("signature");
      var mockSession = IdCardSignatureSession.builder().build();
      var user = sampleAuthenticatedPersonAndMember().build();

      when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.persistIdCardSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq("signature"), eq(Locale.ENGLISH)))
          .thenReturn(OUTSTANDING_TRANSACTION);

      var result =
          mandateBatchSignatureService.persistIdCardSignature(mandateBatchId, finishCommand, user);

      assertThat(result.getStatusCode()).isEqualTo(OUTSTANDING_TRANSACTION);
    }

    @Test
    void persistIdCardSignatureRequiresAnIdCardSignatureSession() {
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand("signature");
      var user = sampleAuthenticatedPersonAndMember().build();

      when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> mandateBatchSignatureService.persistIdCardSignature(1L, finishCommand, user))
          .isInstanceOf(IdSessionException.class);
      verify(mandateBatchService, never())
          .persistIdCardSignature(any(), any(), any(), any(), any());
    }

    @Test
    void getIdCardSignatureStatusReturnsTheProcessingStatus() {
      var mandateBatchId = 1L;
      var user = sampleAuthenticatedPersonAndMember().build();

      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.getIdCardSignatureStatus(
              any(), eq(mandateBatchId), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      var result = mandateBatchSignatureService.getIdCardSignatureStatus(mandateBatchId, user);

      assertThat(result.getStatusCode()).isEqualTo(SIGNATURE);
    }
  }
}
