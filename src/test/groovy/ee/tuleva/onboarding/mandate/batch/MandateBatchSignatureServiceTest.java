package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.SIGNATURE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import java.util.Locale;
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

  @InjectMocks private MandateBatchSignatureService mandateBatchSignatureService;

  @Nested
  @DisplayName("mobile id")
  class MobileIdTests {

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
  }

  @Nested
  @DisplayName("smart id")
  class SmartIdTests {

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
    @DisplayName("persistIdCardAndGetProcessingStatus returns finished status code")
    void finishIdCardSignatureReturnsStatusCode() {
      var mandateBatchId = 1L;
      var signedHash = "signedHash";
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand(signedHash);
      var mockSession = IdCardSignatureSession.builder().build();

      when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
              any(), eq(mandateBatchId), eq(mockSession), eq(signedHash), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      var user = sampleAuthenticatedPersonAndMember().build();
      var result =
          mandateBatchSignatureService.persistIdCardSignedHashAndGetProcessingStatus(
              mandateBatchId, finishCommand, user);

      assertThat(result.getStatusCode()).isEqualTo(SIGNATURE);
    }
  }
}
