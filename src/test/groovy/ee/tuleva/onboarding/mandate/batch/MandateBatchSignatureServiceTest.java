package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
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
      var user =
          authenticatedPersonFromUser(sampleUser().build())
              .attributes(Map.of(PHONE_NUMBER, phoneNumber))
              .build();

      when(mandateBatchService.mobileIdSign(eq(mandateBatchId), any(), eq(phoneNumber)))
          .thenReturn(mockSession);

      var result = mandateBatchSignatureService.startMobileIdSignature(mandateBatchId, user);

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
      when(mandateBatchService.finalizeMobileIdSignature(
              any(), eq(mandateBatchId), any(), eq(Locale.ENGLISH)))
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
    @DisplayName("start smart id signature returns null challenge code")
    void startSmartIdSignatureReturnsNullChallengeCode() {
      var mandateBatchId = 1L;
      var mockSession = new SmartIdSignatureSession("certSessionId", "personalCode", null);
      mockSession.setVerificationCode(null);

      when(mandateBatchService.smartIdSign(eq(mandateBatchId), any())).thenReturn(mockSession);
      var user = sampleAuthenticatedPersonAndMember().build();
      var result = mandateBatchSignatureService.startSmartIdSignature(mandateBatchId, user);

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
      when(mandateBatchService.finalizeSmartIdSignature(
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
    @DisplayName("start id card signature returns the hash to be signed by the client")
    void startIdCardSignatureReturnsHash() {
      var mandateBatchId = 1L;
      var clientCertificate = "clientCertificate";
      var startCommand = MandateFixture.sampleStartIdCardSignCommand(clientCertificate);
      var mockSession = IdCardSignatureSession.builder().hashToSignInHex("asdfg").build();

      when(mandateBatchService.idCardSign(eq(mandateBatchId), any(), eq(clientCertificate)))
          .thenReturn(mockSession);

      var user = sampleAuthenticatedPersonAndMember().build();
      var result = mandateBatchSignatureService.startIdCardSign(mandateBatchId, user, startCommand);

      assertThat(result.getHash()).isEqualTo("asdfg");
      verify(sessionStore, times(1)).save(mockSession);
    }

    @Test
    @DisplayName("finish id card signature returns the status code")
    void finishIdCardSignatureReturnsStatusCode() {
      var mandateBatchId = 1L;
      var signedHash = "signedHash";
      var finishCommand = MandateFixture.sampleFinishIdCardSignCommand(signedHash);
      var mockSession = IdCardSignatureSession.builder().build();

      when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(mockSession));
      when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
      when(mandateBatchService.finalizeIdCardSignature(
              any(), eq(mandateBatchId), eq(mockSession), eq(signedHash), eq(Locale.ENGLISH)))
          .thenReturn(SIGNATURE);

      var user = sampleAuthenticatedPersonAndMember().build();
      var result =
          mandateBatchSignatureService.getIdCardSignatureStatus(
              mandateBatchId, finishCommand, user);

      assertThat(result.getStatusCode()).isEqualTo(SIGNATURE);
    }
  }
}
