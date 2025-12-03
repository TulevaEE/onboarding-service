package ee.tuleva.onboarding.mandate.email.persistence;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
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
      emailPersistenceService.cancel(event.user(), EmailType.THIRD_PILLAR_SUGGEST_SECOND);
    }
  }
}
