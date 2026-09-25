package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_SUGGEST_SECOND;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER;
import static java.time.temporal.ChronoUnit.DAYS;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.Emailable;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecondPillarLetterScheduler {

  @Getter
  @RequiredArgsConstructor
  public enum Trigger {
    MANDATE("mandate"),
    PAYMENT_ARRIVED("payment_arrived");

    private final String tag;
  }

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final Clock clock;

  public <T extends Person & Emailable> void schedule(T recipient, Locale locale, Trigger trigger) {
    if (emailPersistenceService.hasPendingOrSentEmail(recipient, THIRD_PILLAR_SUGGEST_SECOND)) {
      log.info("Already has the second pillar letter, not scheduling another: trigger={}", trigger);
      return;
    }
    String templateName = THIRD_PILLAR_SUGGEST_SECOND.getTemplateName(locale);
    MandrillMessage message =
        emailService.newMandrillMessage(
            recipient.getEmail(),
            templateName,
            nameMergeVars(recipient),
            List.of("pillar_3.1", "suggest_2", trigger.getTag()));
    emailService
        .send(recipient, message, templateName, Instant.now(clock).plus(3, DAYS))
        .ifPresentOrElse(
            response ->
                emailPersistenceService.save(
                    recipient,
                    response.getId(),
                    THIRD_PILLAR_SUGGEST_SECOND,
                    response.getStatus(),
                    SECOND_PILLAR_TRANSFER.getTag()),
            () -> log.error("Second pillar letter failed to schedule: trigger={}", trigger));
  }

  private static Map<String, Object> nameMergeVars(Person recipient) {
    return Map.of(
        "fname", Names.formatted(recipient.getFirstName()),
        "lname", Names.formatted(recipient.getLastName()));
  }
}
