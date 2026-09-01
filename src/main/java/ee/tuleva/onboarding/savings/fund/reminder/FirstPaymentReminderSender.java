package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;

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
class FirstPaymentReminderSender {

  private static final List<String> TAGS = List.of("savings_fund", "first_payment_reminder");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundFees savingsFundFees;

  void send(FirstPaymentReminder reminder) {
    String templateName =
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON.getTemplateName(reminder.locale());

    var message =
        emailService.newMandrillMessage(
            reminder.email(),
            templateName,
            Map.of(
                "fname",
                Names.formatted(reminder.firstName()),
                "savingsFundFee",
                savingsFundFees.ongoingChargesPercent(reminder.locale())),
            TAGS);

    emailService
        .send(reminder, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    reminder,
                    response.getId(),
                    SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON,
                    response.getStatus()));
  }
}
