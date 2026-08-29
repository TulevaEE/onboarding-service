package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledEmailCanceller {

  private final EmailPersistenceService emailPersistenceService;

  @EventListener
  public void cancelEmail(AfterMandateSignedEvent event) {
    if (event.getPillar() == 2) {
      emailPersistenceService.cancel(event.getUser(), EmailType.THIRD_PILLAR_SUGGEST_SECOND);
    }
  }
}
