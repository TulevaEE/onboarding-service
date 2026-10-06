package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;
import static java.util.Locale.ENGLISH;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
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
class ChildOnboardingAbandonmentReminderSenderTest {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_abandonment");

  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private SavingsFundFees savingsFundFees;

  @InjectMocks private ChildOnboardingAbandonmentReminderSender sender;

  private final PersonImpl parent = new PersonImpl("38812121215", "mari", "Example");

  @Test
  void sendsTheEstonianReminderToTheParentAndRecordsItAgainstTheParent() {
    var message = message(ESTONIAN, "savings_fund_onboarding_abandonment_child_et", "0,28");
    var response = mandrillResponse("message-id", "sent");
    given(emailService.send(parent, message, "savings_fund_onboarding_abandonment_child_et"))
        .willReturn(Optional.of(response));

    sender.send(reminder(ESTONIAN));

    verify(emailPersistenceService)
        .save(parent, "message-id", SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, "sent");
  }

  @Test
  void sendsTheEnglishReminderToAParentWhoPrefersEnglish() {
    var message = message(ENGLISH, "savings_fund_onboarding_abandonment_child_en", "0.28");
    var response = mandrillResponse("message-id", "sent");
    given(emailService.send(parent, message, "savings_fund_onboarding_abandonment_child_en"))
        .willReturn(Optional.of(response));

    sender.send(reminder(ENGLISH));

    verify(emailPersistenceService)
        .save(parent, "message-id", SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, "sent");
  }

  @Test
  void recordsNothingWhenMandrillDoesNotAcceptTheMessage() {
    var message = message(ESTONIAN, "savings_fund_onboarding_abandonment_child_et", "0,28");
    given(emailService.send(parent, message, "savings_fund_onboarding_abandonment_child_et"))
        .willReturn(Optional.empty());

    sender.send(reminder(ESTONIAN));

    verifyNoInteractions(emailPersistenceService);
  }

  private ChildOnboardingAbandonmentReminder reminder(Locale locale) {
    return new ChildOnboardingAbandonmentReminder(1L, parent, "parent@example.com", locale);
  }

  private MandrillMessage message(Locale locale, String template, String fee) {
    var message = new MandrillMessage();
    given(savingsFundFees.ongoingChargesPercent(locale)).willReturn(fee);
    given(
            emailService.newMandrillMessage(
                "parent@example.com",
                template,
                Map.of("fname", "Mari", "savingsFundFee", fee),
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
