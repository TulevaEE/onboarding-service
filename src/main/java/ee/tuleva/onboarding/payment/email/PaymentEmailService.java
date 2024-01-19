package ee.tuleva.onboarding.payment.email;

import static java.util.Collections.emptyList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.email.MandateEmailService;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
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
  private final EmailPersistenceService emailPersistenceService;
  private final MandateEmailService mandateEmailService;

  void sendThirdPillarPaymentSuccessEmail(User user, Payment payment, Locale locale) {
    EmailType emailType = EmailType.from(payment);
    String templateName = emailType.getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            emailType.getTemplateName(locale),
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "recipient", payment.getRecipientPersonalCode()),
            List.of("pillar_3.1", "mandate", "payment"),
            cancelReminderEmailsAndGetMandateAttachment(user));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus()));
  }

  private List<MessageContent> cancelReminderEmailsAndGetMandateAttachment(User user) {
    List<Email> cancelledEmails =
        emailPersistenceService.cancel(user, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE);

    if (cancelledEmails.isEmpty()) {
      return emptyList();
    }

    Email latestScheduledEmail = cancelledEmails.get(0);
    return mandateEmailService.getMandateAttachments(user, latestScheduledEmail.getMandate());
  }
}
