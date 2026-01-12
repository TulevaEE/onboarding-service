package ee.tuleva.onboarding.aml.notification;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.AML;

import ee.tuleva.onboarding.notification.slack.SlackService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlCheckNotifier {

  private final SlackService slackService;

  @EventListener
  public void onAmlCheckCreated(AmlCheckCreatedEvent event) {
    if (List.of(POLITICALLY_EXPOSED_PERSON, POLITICALLY_EXPOSED_PERSON_AUTO, SANCTION, RISK_LEVEL)
            .contains(event.getAmlCheckType())
        && event.isFailed()) {
      slackService.sendMessage(
          "AML check failed: checkId=%d, type=%s"
              .formatted(event.getCheckId(), event.getAmlCheckType()),
          AML);
    }
  }

  @EventListener
  public void onScheduledAmlCheckJobRun(AmlChecksRunEvent event) {
    slackService.sendMessage(
        "Running AML checks job: numberOfRecords=%d".formatted(event.getNumberOfRecords()), AML);
  }

  @EventListener
  public void onAmlRiskLevelJobRun(AmlRiskLevelJobRunEvent event) {
    slackService.sendMessage(
        "Ran AML Risk Level job: highRiskRecordCount=%d, amlChecksCreatedCount=%d"
            .formatted(event.getHighRiskRowCount(), event.getAmlChecksCreatedCount()),
        AML);
  }
}
