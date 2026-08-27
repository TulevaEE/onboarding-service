package ee.tuleva.onboarding.notification.email;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.MergeVarBucket;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MergeVar;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.config.EmailConfiguration;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

  private static final Set<String> FAILED_STATUSES = Set.of("rejected", "invalid");

  private final EmailConfiguration emailConfiguration;
  private final MandrillApi mandrillApi;
  private final RetryTemplate emailServiceRetryTemplate;

  public EmailService(
      EmailConfiguration emailConfiguration,
      @Autowired(required = false) MandrillApi mandrillApi,
      RetryTemplate emailServiceRetryTemplate) {
    this.emailConfiguration = emailConfiguration;
    this.mandrillApi = mandrillApi;
    this.emailServiceRetryTemplate = emailServiceRetryTemplate;
  }

  public MandrillMessage newMandrillMessage(
      String to,
      String templateName,
      Map<String, Object> mergeVars,
      List<String> tags,
      List<MessageContent> attachments) {
    MandrillMessage message = new MandrillMessage();

    message.setAutoText(true);
    MergeVarBucket mergeVarBucket = new MergeVarBucket();
    mergeVarBucket.setRcpt(to);
    MergeVar[] vars =
        withoutSelfPromotion(templateName, mergeVars).entrySet().stream()
            .map(entry -> new MergeVar(entry.getKey(), entry.getValue()))
            .toList()
            .toArray(new MergeVar[0]);
    mergeVarBucket.setVars(vars);
    message.setMergeVars(List.of(mergeVarBucket));
    message.setAttachments(attachments);
    Recipient recipient = new Recipient();
    recipient.setEmail(to);
    message.setTo(List.of(recipient));
    message.setPreserveRecipients(true);
    message.setTags(tags);

    message.setImportant(true);
    message.setTrackClicks(true);
    message.setTrackOpens(true);
    message.setGoogleAnalyticsDomains(List.of("tuleva.ee", "pension.tuleva.ee"));
    message.setGoogleAnalyticsCampaign(templateName);

    return message;
  }

  public MandrillMessage newMandrillMessage(
      String to,
      String replyTo,
      String templateName,
      Map<String, Object> mergeVars,
      List<String> tags,
      List<MessageContent> attachments) {
    MandrillMessage message = newMandrillMessage(to, templateName, mergeVars, tags, attachments);
    message.setHeaders(Map.of("Reply-To", replyTo));

    return message;
  }

  public Optional<MandrillMessageStatus> send(
      Person person, MandrillMessage message, String templateName) {
    return send(person, message, templateName, null);
  }

  private static String recipientOf(MandrillMessage message) {
    if (message.getTo() == null || message.getTo().isEmpty()) {
      return null;
    }
    String email = message.getTo().getFirst().getEmail();
    return email == null || email.isBlank() ? null : email;
  }

  public Optional<MandrillMessageStatus> send(
      Person person, MandrillMessage message, String templateName, Instant sendAt) {
    if (mandrillApi == null) {
      log.warn(
          "Mandrill not initialised, not sending email for person: personalCode={}, sendAt={}, templateName={}",
          person.getPersonalCode(),
          sendAt,
          templateName);
      return Optional.empty();
    }

    if (recipientOf(message) == null) {
      log.warn(
          "Message has no recipient, not sending: personalCode={}, templateName={}",
          person.getPersonalCode(),
          templateName);
      return Optional.empty();
    }

    try {
      Date sendDate = sendAt != null ? Date.from(sendAt) : null;
      log.info(
          "Sending email to person: personalCode={}, sendAt={}, templateName={}",
          person.getPersonalCode(),
          sendDate,
          templateName);

      MandrillMessageStatus response =
          mandrillApi.messages()
              .sendTemplate(templateName, Map.of(), message, false, null, sendDate)[0];
      log.info(
          "Mandrill API response: status={}, id={}, rejectReason={}",
          response.getStatus(),
          response.getId(),
          response.getRejectReason());

      if (response.getStatus() != null && FAILED_STATUSES.contains(response.getStatus())) {
        log.warn(
            "Mandrill did not deliver email: personalCode={}, templateName={}, status={}, rejectReason={}, id={}",
            person.getPersonalCode(),
            templateName,
            response.getStatus(),
            response.getRejectReason(),
            response.getId());
        return Optional.empty();
      }
      return Optional.of(response);

    } catch (MandrillApiError mandrillApiError) {
      log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
      return Optional.empty();
    } catch (Exception e) {
      log.error(
          "Failed to send email: personalCode={}, templateName={}",
          person.getPersonalCode(),
          templateName,
          e);
      return Optional.empty();
    }
  }

  public boolean sendSystemEmail(MandrillMessage message) {
    if (mandrillApi == null) {
      log.warn("Mandrill not initialised, not sending system email");
      return false;
    }

    try {
      emailServiceRetryTemplate.execute(
          () -> {
            log.info("Sending system email: to={}", message.getTo());
            MandrillMessageStatus response = mandrillApi.messages().send(message, false)[0];
            log.info(
                "Mandrill API response: status={}, id={}, rejectReason={}",
                response.getStatus(),
                response.getId(),
                response.getRejectReason());

            if (response.getStatus() != null && FAILED_STATUSES.contains(response.getStatus())) {
              throw new EmailDeliveryException(
                  "Mandrill rejected email: status=%s, rejectReason=%s, id=%s"
                      .formatted(
                          response.getStatus(), response.getRejectReason(), response.getId()));
            }
            return response;
          });
      return true;
    } catch (RetryException e) {
      if (e.getCause() instanceof EmailDeliveryException ede) {
        throw ede;
      }
      log.error("Failed to send system email after retries", e.getCause());
    }
    return false;
  }

  public Optional<MandrillScheduledMessageInfo> cancelScheduledEmail(String mandrillMessageId) {
    try {
      return Optional.of(mandrillApi.messages().cancelScheduled(mandrillMessageId));
    } catch (MandrillApiError mandrillApiError) {
      if ("Unknown_Message".equals(mandrillApiError.getMandrillErrorName())) {
        log.info(
            "Mandrill email already sent out?, cannot cancel: {}",
            mandrillApiError.getMandrillErrorAsJson());
      } else {
        log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
      }
    } catch (IOException e) {
      log.error(e.getLocalizedMessage(), e);
    }
    return Optional.empty();
  }

  private static Map<String, Object> withoutSelfPromotion(
      String templateName, Map<String, Object> mergeVars) {
    Map<String, Object> scrubbed = new HashMap<>(mergeVars);
    if (templateName.startsWith("third_pillar") || templateName.startsWith("withdrawal_batch")) {
      scrubbed.replace("suggestThirdPillar", false);
    }
    if (templateName.startsWith("second_pillar")
        || templateName.startsWith("payment_rate")
        || templateName.startsWith("withdrawal_batch")) {
      scrubbed.replace("suggestSecondPillar", false);
    }
    if (templateName.contains("payment_rate") || templateName.startsWith("withdrawal_batch")) {
      scrubbed.replace("suggestPaymentRate", false);
    }
    if (templateName.startsWith("withdrawal_batch")) {
      scrubbed.replace("suggestThirdPillarRaise", false);
    }
    if (templateName.startsWith("savings_fund")) {
      scrubbed.replace("suggestSavingsFund", false);
    }
    return scrubbed;
  }
}
