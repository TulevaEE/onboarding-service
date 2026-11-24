package ee.tuleva.onboarding.mandate.email;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.user.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

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

  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
  public void cancelSecondPillarLetter(UserCreatedEvent event) {
    emailPersistenceService.cancel(event.user(), EmailType.THIRD_PILLAR_SUGGEST_SECOND);
  }
}
