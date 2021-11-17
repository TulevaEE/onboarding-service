package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledEmailCanceller {

  private final ScheduledEmailService scheduledEmailService;

  @EventListener
  public void cancelEmail(AfterMandateSignedEvent event) {
    if (event.getPillar() == 2) {
      scheduledEmailService.cancel(event.getUser(), ScheduledEmailType.SUGGEST_SECOND_PILLAR);
    }
  }
}
