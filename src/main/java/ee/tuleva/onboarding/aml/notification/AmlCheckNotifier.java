package ee.tuleva.onboarding.aml.notification;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlCheckNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onAmlCheckCreated(AmlCheckCreatedEvent event) {
    if (List.of(POLITICALLY_EXPOSED_PERSON, POLITICALLY_EXPOSED_PERSON_AUTO, SANCTION, RISK_LEVEL)
            .contains(event.getAmlCheckType())
        && event.isFailed()) {
      notificationService.sendMessage(
          "AML check failed: checkId=%d, type=%s"
              .formatted(event.getCheckId(), event.getAmlCheckType()),
          AML);
    }
  }

  @EventListener
  public void onScheduledAmlCheckJobRun(AmlChecksRunEvent event) {
    notificationService.sendMessage(
        "Running AML checks job: numberOfRecords=%d".formatted(event.getNumberOfRecords()), AML);
  }

  @EventListener
  public void onAmlRiskLevelJobRun(AmlRiskLevelJobRunEvent event) {
    notificationService.sendMessage(
        "Ran AML Risk Level job: highRiskRecordCount=%d, amlChecksCreatedCount=%d"
            .formatted(event.getHighRiskRowCount(), event.getAmlChecksCreatedCount()),
        AML);
  }
}
