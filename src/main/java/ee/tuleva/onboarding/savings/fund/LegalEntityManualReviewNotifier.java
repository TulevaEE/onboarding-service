package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;

import ee.tuleva.onboarding.kyb.KybMonitoringCompletedEvent;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LegalEntityManualReviewNotifier {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final OperationsNotificationService notificationService;

  @EventListener
  public void onKybMonitoringCompleted(KybMonitoringCompletedEvent event) {
    List<String> registryCodes =
        savingsFundOnboardingRepository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(
            event.startedAt());
    if (registryCodes.isEmpty()) {
      return;
    }
    notificationService.sendMessage(
        "KYB ownership verification inconclusive, manual review needed: companyCount=%d, registryCodes=%s"
            .formatted(registryCodes.size(), String.join(", ", registryCodes)),
        AML);
  }
}
