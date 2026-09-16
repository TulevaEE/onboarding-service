package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.*;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.nudge.NudgeDecision;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.user.User;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEmailService {

  private static final String SAVINGS_FUND_TAG = "savings_fund";

  private final MandateRepository mandateRepository;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;

  void sendThirdPillarPaymentSuccessEmail(
      User user, Payment payment, NudgeDecision decision, Locale locale) {
    EmailType emailType = EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE;
    String templateName = emailType.getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, payment, decision, locale),
            List.of("pillar_3.1", "mandate", "payment", decision.tag()),
            cancelReminderEmailsAndGetMandateAttachment(user));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), decision.tag()));
  }

  void sendSavingsFundPaymentEmail(
      User user, SavingsFundPaymentEmail email, NudgeDecision decision, Locale locale) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(decision.mergeVars(locale));
    mergeVars.putAll(email.mergeVars());

    sendSavingsFundEmail(
        user, email, mergeVars, List.of(SAVINGS_FUND_TAG, decision.tag()), decision.tag(), locale);
  }

  void sendSavingsFundPaymentEmail(User user, SavingsFundPaymentEmail email, Locale locale) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(email.mergeVars());

    sendSavingsFundEmail(user, email, mergeVars, List.of(SAVINGS_FUND_TAG), null, locale);
  }

  private void sendSavingsFundEmail(
      User user,
      SavingsFundPaymentEmail email,
      Map<String, Object> mergeVars,
      List<String> tags,
      @Nullable String nudge,
      Locale locale) {
    String templateName = email.emailType().getTemplateName(locale);

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(user.getEmail(), templateName, mergeVars, tags);
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response -> {
              if (nudge == null) {
                emailPersistenceService.save(
                    user, response.getId(), email.emailType(), response.getStatus());
              } else {
                emailPersistenceService.save(
                    user, response.getId(), email.emailType(), response.getStatus(), nudge);
              }
            });
  }

  private Map<String, Object> getMergeVars(
      User user, Payment payment, NudgeDecision decision, Locale locale) {
    Map<String, Object> variables =
        new HashMap<>(
            Map.of(
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "senderPersonalCode", user.getPersonalCode(),
                "recipientPersonalCode", payment.getRecipientPersonalCode()));
    variables.putAll(getNameMergeVars(user));
    variables.putAll(decision.mergeVars(locale));

    return variables;
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
        .map(mandate -> getAttachments(user, mandate))
        .orElse(emptyList());
  }
}
