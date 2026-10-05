package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChildOnboardingAbandonmentReminderSender {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_abandonment");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundFees savingsFundFees;

  void send(ChildOnboardingAbandonmentReminder reminder) {
    String templateName = SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD.getTemplateName(ESTONIAN);

    var message =
        emailService.newMandrillMessage(
            reminder.parentEmail(), templateName, mergeVars(reminder), TAGS);

    emailService
        .send(reminder.parent(), message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    reminder.parent(),
                    response.getId(),
                    SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD,
                    response.getStatus()));
  }

  private Map<String, Object> mergeVars(ChildOnboardingAbandonmentReminder reminder) {
    return Map.of(
        "fname", Names.formatted(reminder.parent().getFirstName()),
        "savingsFundFee", savingsFundFees.ongoingChargesPercent(ESTONIAN));
  }
}
