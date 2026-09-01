package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstPaymentReminderSenderTest {

  private static final String SAVER = "38812121215";
  private static final List<String> TAGS = List.of("savings_fund", "first_payment_reminder");

  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private SavingsFundFees savingsFundFees;

  @InjectMocks private FirstPaymentReminderSender sender;

  private final FirstPaymentReminder estonianSaver =
      new FirstPaymentReminder(SAVER, "Saver", "Example", "saver@example.com", Locale.of("et"));
  private final FirstPaymentReminder englishSpeakingSaver =
      new FirstPaymentReminder(SAVER, "Saver", "Example", "saver@example.com", Locale.ENGLISH);

  @Test
  void sendsTheEstonianReminderAndRecordsIt() {
    var message = messageFor(estonianSaver, "savings_fund_first_payment_reminder_person_et");
    var response = mandrillResponse("message-id", "sent");
    given(
            emailService.send(
                estonianSaver, message, "savings_fund_first_payment_reminder_person_et"))
        .willReturn(Optional.of(response));

    sender.send(estonianSaver);

    verify(emailPersistenceService)
        .save(estonianSaver, "message-id", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON, "sent");
  }

  @Test
  void sendsTheEnglishReminderToSaversWhoPreferEnglish() {
    var message = messageFor(englishSpeakingSaver, "savings_fund_first_payment_reminder_person_en");
    var response = mandrillResponse("message-id", "sent");
    given(
            emailService.send(
                englishSpeakingSaver, message, "savings_fund_first_payment_reminder_person_en"))
        .willReturn(Optional.of(response));

    sender.send(englishSpeakingSaver);

    verify(emailPersistenceService)
        .save(
            englishSpeakingSaver, "message-id", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON, "sent");
  }

  @Test
  void recordsNothingWhenMandrillDoesNotAcceptTheMessage() {
    var message = messageFor(estonianSaver, "savings_fund_first_payment_reminder_person_et");
    given(
            emailService.send(
                estonianSaver, message, "savings_fund_first_payment_reminder_person_et"))
        .willReturn(Optional.empty());

    sender.send(estonianSaver);

    verifyNoInteractions(emailPersistenceService);
  }

  private MandrillMessage messageFor(FirstPaymentReminder reminder, String templateName) {
    var message = new MandrillMessage();
    given(savingsFundFees.ongoingChargesPercent(reminder.locale())).willReturn("0.28");
    given(
            emailService.newMandrillMessage(
                reminder.email(),
                templateName,
                Map.of("fname", reminder.firstName(), "savingsFundFee", "0.28"),
                TAGS))
        .willReturn(message);
    return message;
  }

  private MandrillMessageStatus mandrillResponse(String id, String status) {
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn(id);
    given(response.getStatus()).willReturn(status);
    return response;
  }
}
