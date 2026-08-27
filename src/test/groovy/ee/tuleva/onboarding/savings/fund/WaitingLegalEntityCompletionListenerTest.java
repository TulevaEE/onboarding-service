package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class WaitingLegalEntityCompletionListenerTest {

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final LegalEntityScreener screener = mock(LegalEntityScreener.class);
  private final WaitingLegalEntityCompletionListener listener =
      new WaitingLegalEntityCompletionListener(
          repository, screener, mock(PlatformTransactionManager.class));

  private KycCheckPerformedEvent event(RiskLevel riskLevel) {
    return new KycCheckPerformedEvent(
        this, "38001010000", new KycCheck(riskLevel, Map.of()), IDENTITY_ONLY);
  }

  @Test
  void rescreensEveryWaitingCompanyWhenSomeoneBecomesVerified() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of("11111111", "22222222"));

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest("11111111");
    verify(screener).screenLatest("22222222");
  }

  @Test
  void treatsNoRiskAsVerified() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of("11111111"));

    listener.onKycCheckPerformed(event(NONE));

    verify(screener).screenLatest("11111111");
  }

  // Medium and high risk do not produce a successful aml_check, so nothing can have
  // been unblocked and the registry must not be called.
  @Test
  void doesNothingWhenTheRiskLevelIsMedium() {
    listener.onKycCheckPerformed(event(MEDIUM));

    verifyNoInteractions(repository, screener);
  }

  @Test
  void doesNothingWhenTheRiskLevelIsHigh() {
    listener.onKycCheckPerformed(event(HIGH));

    verifyNoInteractions(repository, screener);
  }

  @Test
  void doesNotTouchTheRegistryWhenNoCompanyIsWaiting() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of());

    listener.onKycCheckPerformed(event(LOW));

    verifyNoInteractions(screener);
  }

  // A personal onboarding verifies the person just as well as an identity-only one.
  @Test
  void rescreensRegardlessOfWhyThePersonWasVerified() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of("11111111"));

    listener.onKycCheckPerformed(
        new KycCheckPerformedEvent(
            this, "38001010000", new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING));

    verify(screener).screenLatest("11111111");
  }

  @Test
  void keepsGoingWhenOneCompanyFailsToRescreen() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of("11111111", "22222222"));
    doThrow(new RuntimeException("registry down")).when(screener).screenLatest("11111111");

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest("22222222");
  }

  @Test
  void doesNotChangeAnyStatusItself() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of("11111111"));

    listener.onKycCheckPerformed(event(LOW));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }
}
