package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.kyb.KybMonitoringCompletedEvent;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalEntityManualReviewNotifierTest {

  private static final Instant RUN_STARTED_AT = Instant.parse("2026-07-02T00:00:00Z");

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final LegalEntityManualReviewNotifier notifier =
      new LegalEntityManualReviewNotifier(repository, notificationService);

  @Test
  void sendsOneSummaryMessageToAmlChannelWhenCompaniesNeedManualReview() {
    given(repository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(RUN_STARTED_AT))
        .willReturn(List.of("11111111", "22222222"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verify(notificationService)
        .sendMessage(
            "KYB ownership verification inconclusive, manual review needed: companyCount=2, registryCodes=11111111, 22222222",
            AML);
  }

  @Test
  void doesNotPropagateNotificationFailures() {
    given(repository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(RUN_STARTED_AT))
        .willReturn(List.of("11111111"));
    willThrow(new IllegalStateException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));
  }

  @Test
  void doesNotPropagateQueryFailures() {
    given(repository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(RUN_STARTED_AT))
        .willThrow(new IllegalStateException("db down"));

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingWhenNoCompaniesNeedManualReview() {
    given(repository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(RUN_STARTED_AT))
        .willReturn(List.of());

    notifier.onKybMonitoringCompleted(new KybMonitoringCompletedEvent(RUN_STARTED_AT));

    verifyNoInteractions(notificationService);
  }
}
