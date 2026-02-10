package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.*;
import static java.util.Collections.emptyList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments;
import ee.tuleva.onboarding.mandate.email.PillarSuggestion;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.user.User;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;

  void sendThirdPillarPaymentSuccessEmail(
      User user, Payment payment, PillarSuggestion pillarSuggestion, Locale locale) {
    EmailType emailType = EmailType.from(payment);
    String templateName = emailType.getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            emailType.getTemplateName(locale),
            getMergeVars(user, payment, pillarSuggestion),
            getTags(pillarSuggestion),
            cancelReminderEmailsAndGetMandateAttachment(user));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus()));
  }

  void sendSavingsFundPaymentEmail(
      User user, EmailType emailType, PillarSuggestion pillarSuggestion, Locale locale) {
    String templateName = emailType.getTemplateName(locale);
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(getPillarSuggestionMergeVars(pillarSuggestion));

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(), templateName, mergeVars, getSavingsFundTags(pillarSuggestion), null);
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus()));
  }

  private Map<String, Object> getMergeVars(
      User user, Payment payment, PillarSuggestion pillarSuggestion) {
    Map<String, Object> variables =
        new HashMap<>(
            Map.of(
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "senderPersonalCode", user.getPersonalCode(),
                "recipientPersonalCode", payment.getRecipientPersonalCode()));
    variables.putAll(getNameMergeVars(user));
    variables.putAll(getPillarSuggestionMergeVars(pillarSuggestion));

    return variables;
  }

  private List<String> getTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("pillar_3.1");
    tags.add("mandate");
    tags.add("payment");
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestSecondPillar()) {
      tags.add("suggest_2");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }

    return tags;
  }

  private List<String> getSavingsFundTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("savings_fund");
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestSecondPillar()) {
      tags.add("suggest_2");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    return tags;
  }

  private List<MessageContent> cancelReminderEmailsAndGetMandateAttachment(User user) {
    List<Email> cancelledEmails =
        emailPersistenceService.cancel(user, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE);

    if (cancelledEmails.isEmpty()) {
      return emptyList();
    }

    Email latestScheduledEmail = cancelledEmails.getFirst();
    return EmailVariablesAttachments.getAttachments(user, latestScheduledEmail.getMandate());
  }
}
