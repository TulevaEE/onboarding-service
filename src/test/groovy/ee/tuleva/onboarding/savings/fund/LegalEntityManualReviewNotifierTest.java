package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.KybMonitoringCompletedEvent;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalEntityManualReviewNotifierTest {

  private static final Instant RUN_STARTED_AT = Instant.parse("2026-07-02T00:00:00Z");
  private static final List<KybCheckType> OWNERSHIP_CHECKS =
      List.of(SOLE_MEMBER_OWNERSHIP, DUAL_MEMBER_OWNERSHIP, SINGLE_BOARD_MEMBER_OWNERSHIP);
  private static final List<KybCheckType> ACTIVITY_CHECK = List.of(HIGH_RISK_NACE);

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final LegalEntityManualReviewNotifier notifier =
      new LegalEntityManualReviewNotifier(repository, notificationService);

  @Test
  void sendsOneSummaryMessageToAmlChannelWhenOwnershipNeedsManualReview() {
    given(
            repository.findCompletedLegalEntitiesWithFailedChecksSince(
                RUN_STARTED_AT, OWNERSHIP_CHECKS))
        .willReturn(List.of("11111111", "22222222"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verify(notificationService)
        .sendMessage(
            "KYB ownership verification inconclusive, manual review needed: companyCount=2, registryCodes=11111111, 22222222",
            AML);
  }

  @Test
  void sendsOneSummaryMessageToAmlChannelWhenAnOnboardedCompanyHasAHighRiskActivity() {
    given(
            repository.findCompletedLegalEntitiesWithFailedChecksSince(
                RUN_STARTED_AT, ACTIVITY_CHECK))
        .willReturn(List.of("33333333"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verify(notificationService)
        .sendMessage(
            "KYB high-risk business activity at an onboarded company, manual review needed: companyCount=1, registryCodes=33333333",
            AML);
  }

  @Test
  void sendsTheActivityDigestEvenWhenTheOwnershipQueryFails() {
    given(
            repository.findCompletedLegalEntitiesWithFailedChecksSince(
                RUN_STARTED_AT, OWNERSHIP_CHECKS))
        .willThrow(new IllegalStateException("db down"));
    given(
            repository.findCompletedLegalEntitiesWithFailedChecksSince(
                RUN_STARTED_AT, ACTIVITY_CHECK))
        .willReturn(List.of("33333333"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verify(notificationService)
        .sendMessage(
            "KYB high-risk business activity at an onboarded company, manual review needed: companyCount=1, registryCodes=33333333",
            AML);
  }

  @Test
  void doesNotPropagateNotificationFailures() {
    given(
            repository.findCompletedLegalEntitiesWithFailedChecksSince(
                RUN_STARTED_AT, OWNERSHIP_CHECKS))
        .willReturn(List.of("11111111"));
    willThrow(new IllegalStateException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));
  }

  @Test
  void doesNotPropagateQueryFailures() {
    given(repository.findCompletedLegalEntitiesWithFailedChecksSince(any(), any()))
        .willThrow(new IllegalStateException("db down"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingWhenNoCompaniesNeedManualReview() {
    given(repository.findCompletedLegalEntitiesWithFailedChecksSince(any(), any()))
        .willReturn(List.of());

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verifyNoInteractions(notificationService);
  }
}
