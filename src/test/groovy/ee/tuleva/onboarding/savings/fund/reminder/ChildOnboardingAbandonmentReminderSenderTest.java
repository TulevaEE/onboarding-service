package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;
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
class ChildOnboardingAbandonmentReminderSenderTest {

  private static final String TEMPLATE = "savings_fund_onboarding_abandonment_child_et";
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_abandonment");

  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private SavingsFundFees savingsFundFees;

  @InjectMocks private ChildOnboardingAbandonmentReminderSender sender;

  private final ChildOnboardingAbandonmentReminder parent =
      new ChildOnboardingAbandonmentReminder(
          1L, "38812121215", "mari", "Example", "parent@example.com");

  @Test
  void sendsTheEstonianReminderToTheParentAndRecordsItAgainstTheParent() {
    var message = message();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.of(response));

    sender.send(parent);

    verify(emailPersistenceService)
        .save(parent, "message-id", SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, "sent");
  }

  @Test
  void recordsNothingWhenMandrillDoesNotAcceptTheMessage() {
    var message = message();
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.empty());

    sender.send(parent);

    verifyNoInteractions(emailPersistenceService);
  }

  private MandrillMessage message() {
    var message = new MandrillMessage();
    given(savingsFundFees.ongoingChargesPercent(Locale.of("et"))).willReturn("0,28");
    given(
            emailService.newMandrillMessage(
                "parent@example.com",
                TEMPLATE,
                Map.of("fname", "Mari", "savingsFundFee", "0,28"),
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
