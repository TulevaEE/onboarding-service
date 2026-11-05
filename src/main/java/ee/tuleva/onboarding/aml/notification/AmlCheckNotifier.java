package ee.tuleva.onboarding.aml.notification;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

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
    if (List.of(
                POLITICALLY_EXPOSED_PERSON,
                POLITICALLY_EXPOSED_PERSON_AUTO,
                SANCTION,
                RISK_LEVEL,
                TKF_RISK_LEVEL,
                KYC_CHECK,
                CUSTODY_RIGHT)
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
        "Ran %s risk level job: highRiskRecordCount=%d, amlChecksCreatedCount=%d"
            .formatted(
                event.getLabel(), event.getHighRiskRowCount(), event.getAmlChecksCreatedCount()),
        AML);
  }

  @EventListener
  public void onAmlRiskLevelJobFailed(AmlRiskLevelJobFailedEvent event) {
    notificationService.sendMessage(
        "Risk level job failed: pillar=%s, reason=%s".formatted(event.label(), event.reason()),
        AML,
        ERROR);
  }

  @EventListener
  public void onAmlRiskLevelJobFellBack(AmlRiskLevelJobFellBackEvent event) {
    notificationService.sendMessage(
        "Risk level job fell back to the materialized views: pillar=%s, reason=%s"
            .formatted(event.label(), event.reason()),
        AML,
        WARNING);
  }
}
