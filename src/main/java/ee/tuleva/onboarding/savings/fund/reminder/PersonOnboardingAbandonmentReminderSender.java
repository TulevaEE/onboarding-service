package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PersonOnboardingAbandonmentReminderSender {

  private static final List<String> TAGS = List.of("savings_fund", "onboarding_abandonment");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundFees savingsFundFees;

  void send(PersonOnboardingAbandonmentReminder reminder) {
    String templateName =
        SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON.getTemplateName(reminder.locale());

    var message =
        emailService.newMandrillMessage(reminder.email(), templateName, mergeVars(reminder), TAGS);

    emailService
        .send(reminder.person(), message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    reminder.person(),
                    response.getId(),
                    SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON,
                    response.getStatus()));
  }

  private Map<String, Object> mergeVars(PersonOnboardingAbandonmentReminder reminder) {
    return Map.of(
        "fname", Names.formatted(reminder.person().getFirstName()),
        "savingsFundFee", savingsFundFees.ongoingChargesPercent(reminder.locale()));
  }
}
