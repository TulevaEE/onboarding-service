package ee.tuleva.onboarding.payment.email;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.emptyList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.email.MandateEmailService;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmail;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEmailService {

  private final EmailService emailService;
  private final ScheduledEmailService scheduledEmailService;
  private final PaymentEmailContentService emailContentService;
  private final MandateEmailService mandateEmailService;
  private final Clock clock;
  private final MessageSource messageSource;

  void sendThirdPillarPaymentSuccessEmail(User user, Locale locale) {
    String subject =
        messageSource.getMessage("mandate.email.thirdPillar.paymentSuccess.subject", null, locale);
    String content = emailContentService.getThirdPillarPaymentSuccessHtml(user, locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            subject,
            content,
            List.of("pillar_3.1", "mandate", "payment"),
            cancelReminderEmailsAndGetMandateAttachment(user));
    emailService.send(user, mandrillMessage);
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

  void scheduleThirdPillarSuggestSecondEmail(User user, Locale locale) {
    String subject =
        messageSource.getMessage("mandate.email.thirdPillar.suggestSecond.subject", null, locale);
    String content = emailContentService.getThirdPillarSuggestSecondHtml(user, locale);
    Instant sendAt = Instant.now(clock).plus(3, DAYS);

    MandrillMessage message =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            subject,
            content,
            List.of("pillar_3.1", "suggest_2"),
            null);
    emailService
        .send(user, message, sendAt)
        .ifPresent(
            messageId ->
                scheduledEmailService.create(
                    user, messageId, ScheduledEmailType.SUGGEST_SECOND_PILLAR));
  }
}
