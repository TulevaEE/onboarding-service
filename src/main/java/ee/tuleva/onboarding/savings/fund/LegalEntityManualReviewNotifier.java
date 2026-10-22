package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;

import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.KybMonitoringCompletedEvent;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@NullMarked
@Component
@RequiredArgsConstructor
class LegalEntityManualReviewNotifier {

  private static final List<KybCheckType> OWNERSHIP_CHECKS =
      Arrays.stream(KybCheckType.values()).filter(KybCheckType::isOwnershipCheck).toList();

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final OperationsNotificationService notificationService;

  @EventListener
  public void onKybMonitoringCompleted(KybMonitoringCompletedEvent event) {
    notifyIfCompaniesNeedManualReview(
        event, OWNERSHIP_CHECKS, "KYB ownership verification inconclusive, manual review needed");
    notifyIfCompaniesNeedManualReview(
        event,
        List.of(HIGH_RISK_NACE),
        "KYB high-risk business activity at an onboarded company, manual review needed");
  }

  private void notifyIfCompaniesNeedManualReview(
      KybMonitoringCompletedEvent event, List<KybCheckType> failedChecks, String reason) {
    try {
      List<String> registryCodes =
          savingsFundOnboardingRepository.findCompletedLegalEntitiesWithFailedChecksSince(
              event.startedAt(), failedChecks);
      if (registryCodes.isEmpty()) {
        return;
      }
      notificationService.sendMessage(
          "%s: companyCount=%d, registryCodes=%s"
              .formatted(reason, registryCodes.size(), String.join(", ", registryCodes)),
          AML);
    } catch (Exception e) {
      log.error(
          "Manual review digest failed: startedAt={}, failedChecks={}",
          event.startedAt(),
          failedChecks,
          e);
    }
  }
}
