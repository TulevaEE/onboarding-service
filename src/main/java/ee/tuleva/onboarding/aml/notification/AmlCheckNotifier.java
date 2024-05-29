package ee.tuleva.onboarding.aml.notification;

import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;

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
    if (List.of(POLITICALLY_EXPOSED_PERSON_AUTO, SANCTION).contains(event.getAmlCheckType())
        && event.isFailed()) {
      slackService.sendMessage("AML check failed: " + event.getAmlCheckType());
    }
  }
}
