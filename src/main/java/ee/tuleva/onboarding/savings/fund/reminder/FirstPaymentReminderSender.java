package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.fund.SavingsFundFees;
import ee.tuleva.onboarding.user.Names;
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
    String templateName = reminder.emailType().getTemplateName(reminder.locale());

    var message =
        emailService.newMandrillMessage(
            reminder.recipientEmail(),
            templateName,
            Map.of(
                "fname",
                Names.formatted(reminder.recipientFirstName()),
                "savingsFundFee",
                savingsFundFees.ongoingChargesPercent(reminder.locale())),
            TAGS,
            null);

    emailService
        .send(reminder, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    reminder, response.getId(), reminder.emailType(), response.getStatus()));
  }
}
