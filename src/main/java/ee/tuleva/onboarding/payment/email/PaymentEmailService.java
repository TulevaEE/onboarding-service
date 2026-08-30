package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.*;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.EmailVariablesAttachments;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.SavingsFundCharges;
import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.user.User;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEmailService {

  private static final String SAVINGS_FUND_TAG = "savings_fund";

  private final MandateRepository mandateRepository;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundCharges savingsFundCharges;

  private static EmailType successEmailType(Payment payment) {
    return payment.getPaymentType() == PaymentType.SAVINGS
        ? EmailType.SAVINGS_FUND_PAYMENT_SUCCESS
        : EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE;
  }

  void sendThirdPillarPaymentSuccessEmail(
      User user, Payment payment, PillarSuggestion pillarSuggestion, Locale locale) {
    EmailType emailType = successEmailType(payment);
    String templateName = emailType.getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            emailType.getTemplateName(locale),
            getMergeVars(user, payment, pillarSuggestion, locale),
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
      User user,
      SavingsFundPaymentEmail email,
      PillarSuggestion pillarSuggestion,
      boolean suggestAccountRecurringPayment,
      Locale locale) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(
        getPillarSuggestionMergeVars(
            pillarSuggestion, savingsFundCharges.ongoingChargesPercent(locale)));
    mergeVars.put("suggestAccountRecurringPayment", suggestAccountRecurringPayment);
    mergeVars.putAll(email.mergeVars());

    List<String> tags =
        suggestAccountRecurringPayment
            ? List.of(SAVINGS_FUND_TAG, "nudge_savings_fund_recurring")
            : getSavingsFundTags(pillarSuggestion);
    sendSavingsFundEmail(user, email, mergeVars, tags, locale);
  }

  void sendSavingsFundPaymentEmail(User user, SavingsFundPaymentEmail email, Locale locale) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(email.mergeVars());

    sendSavingsFundEmail(user, email, mergeVars, List.of(SAVINGS_FUND_TAG), locale);
  }

  private void sendSavingsFundEmail(
      User user,
      SavingsFundPaymentEmail email,
      Map<String, Object> mergeVars,
      List<String> tags,
      Locale locale) {
    String templateName = email.emailType().getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(user.getEmail(), templateName, mergeVars, tags);
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), email.emailType(), response.getStatus()));
  }

  private Map<String, Object> getMergeVars(
      User user, Payment payment, PillarSuggestion pillarSuggestion, Locale locale) {
    Map<String, Object> variables =
        new HashMap<>(
            Map.of(
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "senderPersonalCode", user.getPersonalCode(),
                "recipientPersonalCode", payment.getRecipientPersonalCode()));
    variables.putAll(getNameMergeVars(user));
    variables.putAll(
        getPillarSuggestionMergeVars(
            pillarSuggestion, savingsFundCharges.ongoingChargesPercent(locale)));

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

    pillarSuggestion.renderedNudgeTag().ifPresent(tags::add);
    return tags;
  }

  private List<String> getSavingsFundTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add(SAVINGS_FUND_TAG);
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestSecondPillar()) {
      tags.add("suggest_2");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    pillarSuggestion.renderedNudgeTag().ifPresent(tags::add);
    return tags;
  }

  private List<MessageContent> cancelReminderEmailsAndGetMandateAttachment(User user) {
    List<Email> cancelledEmails =
        emailPersistenceService.cancel(user, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE);

    if (cancelledEmails.isEmpty()) {
      return emptyList();
    }

    Email latestScheduledEmail = cancelledEmails.getFirst();
    Long mandateId =
        requireNonNull(
            latestScheduledEmail.getMandateId(),
            "Scheduled reminder email is missing a mandateId: emailId="
                + latestScheduledEmail.getId());
    return mandateRepository
        .findById(mandateId)
        .map(mandate -> EmailVariablesAttachments.getAttachments(user, mandate))
        .orElse(emptyList());
  }
}
