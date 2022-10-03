package ee.tuleva.onboarding.mandate.email;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService;
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateEmailService {

  private final EmailService emailService;
  private final ScheduledEmailService scheduledEmailService;
  private final MandateEmailContentService emailContentService;
  private final Clock clock;
  private final MessageSource messageSource;

  public void sendMandate(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    switch (mandate.getPillar()) {
      case 2 -> sendSecondPillarEmail(user, mandate, pillarSuggestion, locale);
      case 3 -> scheduleThirdPillarPaymentReminderEmail(user, locale);
      default -> throw new IllegalArgumentException("Unknown pillar: " + mandate.getPillar());
    }
  }

  private void sendSecondPillarEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    String subject = messageSource.getMessage("mandate.email.secondPillar.subject", null, locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            subject,
            getSecondPillarContent(user, mandate, pillarSuggestion, locale),
            getSecondPillarMandateTags(pillarSuggestion),
            getMandateAttachments(mandate.getSignedFile(), user, mandate.getId()));
    emailService.send(user, mandrillMessage);
  }

  private List<String> getSecondPillarMandateTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate");
    tags.add("pillar_2");
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    if (pillarSuggestion.isSuggestPillar()) {
      tags.add("suggest_3");
    }
    return tags;
  }

  private String getSecondPillarContent(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    if (mandate.isWithdrawalCancellation()) {
      return emailContentService.getSecondPillarWithdrawalCancellationHtml(user, locale);
    }
    if (mandate.isTransferCancellation()) {
      return emailContentService.getSecondPillarTransferCancellationHtml(user, mandate, locale);
    }
    return emailContentService.getSecondPillarHtml(
        user, mandate.getCreatedDate(), pillarSuggestion, locale);
  }

  private void scheduleThirdPillarPaymentReminderEmail(User user, Locale locale) {
    String subject =
        messageSource.getMessage("mandate.email.thirdPillar.paymentReminder.subject", null, locale);
    String content = emailContentService.getThirdPillarPaymentReminderHtml(user, locale);
    Instant sendAt = Instant.now(clock).plus(1, HOURS);

    MandrillMessage message =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            subject,
            content,
            List.of("pillar_3.1", "reminder"),
            null);
    emailService
        .send(user, message, sendAt)
        .ifPresent(
            messageId ->
                scheduledEmailService.create(
                    user, messageId, ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT));
  }

  private List<MandrillMessage.MessageContent> getMandateAttachments(
      byte[] file, User user, Long mandateId) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(getNameSuffix(user) + "_avaldus_" + mandateId + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(file));

    return singletonList(attachment);
  }

  private String getNameSuffix(User user) {
    String nameSuffix = user.getFirstName() + "_" + user.getLastName();
    nameSuffix = nameSuffix.toLowerCase();
    nameSuffix.replace("õ", "o");
    nameSuffix.replace("ä", "a");
    nameSuffix.replace("ö", "o");
    nameSuffix.replace("ü", "u");
    nameSuffix.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    return nameSuffix;
  }
}
