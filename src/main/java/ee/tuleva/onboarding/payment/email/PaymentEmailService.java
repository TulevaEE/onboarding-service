package ee.tuleva.onboarding.payment.email;

import static java.util.Collections.emptyList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.email.MandateEmailService;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmail;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEmailService {

  private final EmailService emailService;
  private final ScheduledEmailService scheduledEmailService;
  private final MandateEmailService mandateEmailService;

  void sendThirdPillarPaymentSuccessEmail(User user, Payment payment, Locale locale) {
    String templateName = "third_pillar_payment_success_mandate_" + locale.getLanguage();
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "recipient", payment.getRecipientPersonalCode()),
            List.of("pillar_3.1", "mandate", "payment"),
            cancelReminderEmailsAndGetMandateAttachment(user));
    emailService.send(user, mandrillMessage, templateName);
  }

  private List<MessageContent> cancelReminderEmailsAndGetMandateAttachment(User user) {
    List<ScheduledEmail> cancelledEmails =
        scheduledEmailService.cancel(user, ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT);

    if (cancelledEmails.isEmpty()) {
      return emptyList();
    }

    ScheduledEmail latestScheduledEmail = cancelledEmails.get(0);
    return mandateEmailService.getMandateAttachments(user, latestScheduledEmail.getMandate());
  }
}
