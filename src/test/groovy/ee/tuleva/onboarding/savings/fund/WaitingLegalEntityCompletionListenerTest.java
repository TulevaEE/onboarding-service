package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.KybCheckHistory;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class WaitingLegalEntityCompletionListenerTest {

  private static final String VERIFIED_PERSON = "38001010000";
  private static final String ANOTHER_PERSON = "38001010001";
  private static final String FIRST_COMPANY = "11111111";
  private static final String SECOND_COMPANY = "22222222";

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final LegalEntityScreener screener = mock(LegalEntityScreener.class);
  private final KybCheckHistory checkHistory = mock(KybCheckHistory.class);
  private final WaitingLegalEntityCompletionListener listener =
      new WaitingLegalEntityCompletionListener(
          repository, screener, checkHistory, mock(PlatformTransactionManager.class));

  @BeforeEach
  void setUp() {
    when(checkHistory.findIncompleteKycPersonalCodes(any())).thenReturn(List.of(VERIFIED_PERSON));
  }

  private KycCheckPerformedEvent event(RiskLevel riskLevel) {
    return new KycCheckPerformedEvent(
        this, VERIFIED_PERSON, new KycCheck(riskLevel, Map.of()), IDENTITY_ONLY);
  }

  @Test
  void rescreensEveryWaitingCompanyThatIsWaitingForTheVerifiedPerson() {
    when(repository.findPendingLegalEntityCodes())
        .thenReturn(List.of(FIRST_COMPANY, SECOND_COMPANY));

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(FIRST_COMPANY);
    verify(screener).screenLatest(SECOND_COMPANY);
  }

  // A verified person amplifies into one registry round trip per waiting company, so a company
  // that is not waiting for this person must not be screened at all.
  @Test
  void leavesAloneTheCompaniesWaitingForSomebodyElse() {
    when(repository.findPendingLegalEntityCodes())
        .thenReturn(List.of(FIRST_COMPANY, SECOND_COMPANY));
    when(checkHistory.findIncompleteKycPersonalCodes(new RegistryCode(SECOND_COMPANY)))
        .thenReturn(List.of(ANOTHER_PERSON));

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(FIRST_COMPANY);
    verify(screener, never()).screenLatest(SECOND_COMPANY);
  }

  @Test
  void rescreensACompanyWaitingForSeveralPersonsIncludingTheVerifiedOne() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));
    when(checkHistory.findIncompleteKycPersonalCodes(new RegistryCode(FIRST_COMPANY)))
        .thenReturn(List.of(ANOTHER_PERSON, VERIFIED_PERSON));

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(FIRST_COMPANY);
  }

  // Fail open: a company that records nobody to wait for would otherwise be stranded until the
  // nightly monitoring job picks it up.
  @Test
  void rescreensACompanyThatRecordsNobodyToWaitFor() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));
    when(checkHistory.findIncompleteKycPersonalCodes(new RegistryCode(FIRST_COMPANY)))
        .thenReturn(List.of());

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(FIRST_COMPANY);
  }

  @Test
  void rescreensACompanyWhoseWaitingPersonsCannotBeRead() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));
    when(checkHistory.findIncompleteKycPersonalCodes(new RegistryCode(FIRST_COMPANY)))
        .thenThrow(new RuntimeException("unreadable metadata"));

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(FIRST_COMPANY);
  }

  @Test
  void treatsNoRiskAsVerified() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));

    listener.onKycCheckPerformed(event(NONE));

    verify(screener).screenLatest(FIRST_COMPANY);
  }

  // Medium and high risk do not produce a successful aml_check, so nothing can have
  // been unblocked and the registry must not be called.
  @Test
  void doesNothingWhenTheRiskLevelIsMedium() {
    listener.onKycCheckPerformed(event(MEDIUM));

    verifyNoInteractions(repository, screener, checkHistory);
  }

  @Test
  void doesNothingWhenTheRiskLevelIsHigh() {
    listener.onKycCheckPerformed(event(HIGH));

    verifyNoInteractions(repository, screener, checkHistory);
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
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));

    listener.onKycCheckPerformed(
        new KycCheckPerformedEvent(
            this, VERIFIED_PERSON, new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING));

    verify(screener).screenLatest(FIRST_COMPANY);
  }

  @Test
  void keepsGoingWhenOneCompanyFailsToRescreen() {
    when(repository.findPendingLegalEntityCodes())
        .thenReturn(List.of(FIRST_COMPANY, SECOND_COMPANY));
    doThrow(new RuntimeException("registry down")).when(screener).screenLatest(FIRST_COMPANY);

    listener.onKycCheckPerformed(event(LOW));

    verify(screener).screenLatest(SECOND_COMPANY);
  }

  @Test
  void doesNotChangeAnyStatusItself() {
    when(repository.findPendingLegalEntityCodes()).thenReturn(List.of(FIRST_COMPANY));

    listener.onKycCheckPerformed(event(LOW));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }
}
