package ee.tuleva.onboarding.notification.email;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.MergeVarBucket;
import static java.util.function.Predicate.not;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MergeVar;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo;
import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

  private final @Nullable MandrillClient mandrillClient;
  private final RetryTemplate emailServiceRetryTemplate;

  EmailService(
      @Autowired(required = false) @Nullable MandrillClient mandrillClient,
      RetryTemplate emailServiceRetryTemplate) {
    this.mandrillClient = mandrillClient;
    this.emailServiceRetryTemplate = emailServiceRetryTemplate;
  }

  public MandrillMessage newMandrillMessage(
      @Nullable String to, String templateName, Map<String, Object> mergeVars, List<String> tags) {
    return newMandrillMessage(to, templateName, mergeVars, tags, List.of());
  }

  public MandrillMessage newMandrillMessage(
      @Nullable String to,
      String templateName,
      Map<String, Object> mergeVars,
      List<String> tags,
      List<MessageContent> attachments) {
    MandrillMessage message = new MandrillMessage();

    message.setAutoText(true);
    MergeVarBucket mergeVarBucket = new MergeVarBucket();
    mergeVarBucket.setRcpt(to);
    MergeVar[] vars =
        mergeVars.entrySet().stream()
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
      @Nullable String to,
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

  private static @Nullable String recipientOf(MandrillMessage message) {
    if (message.getTo() == null || message.getTo().isEmpty()) {
      return null;
    }
    String email = message.getTo().getFirst().getEmail();
    return email == null || email.isBlank() ? null : email;
  }

  public Optional<MandrillMessageStatus> send(
      Person person, MandrillMessage message, String templateName, @Nullable Instant sendAt) {
    return attemptSend(person, message, templateName, sendAt)
        .filter(not(MandrillRecipientStatuses::hasFailed));
  }

  public Optional<MandrillMessageStatus> sendFromBackgroundJob(
      Person person, MandrillMessage message, String templateName) {
    return attemptSendFromBackgroundJob(person, message, templateName)
        .filter(not(MandrillRecipientStatuses::hasFailed));
  }

  public Optional<MandrillMessageStatus> attemptSend(
      Person person, MandrillMessage message, String templateName) {
    return attemptSend(person, message, templateName, null);
  }

  public Optional<MandrillMessageStatus> attemptSendFromBackgroundJob(
      Person person, MandrillMessage message, String templateName) {
    return attemptSend(
        person,
        message,
        templateName,
        null,
        mandrillClient -> mandrillClient.sendTemplateFromBackgroundJob(templateName, message));
  }

  private Optional<MandrillMessageStatus> attemptSend(
      Person person, MandrillMessage message, String templateName, @Nullable Instant sendAt) {
    return attemptSend(
        person,
        message,
        templateName,
        sendAt,
        mandrillClient -> mandrillClient.sendTemplate(templateName, message, sendAt));
  }

  private Optional<MandrillMessageStatus> attemptSend(
      Person person,
      MandrillMessage message,
      String templateName,
      @Nullable Instant sendAt,
      Function<MandrillClient, MandrillMessageStatus[]> delivery) {
    var mandrillClient = this.mandrillClient;
    if (mandrillClient == null) {
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
      log.info(
          "Sending email to person: personalCode={}, sendAt={}, templateName={}",
          person.getPersonalCode(),
          sendAt,
          templateName);

      MandrillMessageStatus response = delivery.apply(mandrillClient)[0];
      MandrillRecipientStatuses.logStatus(response);

      if (MandrillRecipientStatuses.hasFailed(response)) {
        log.warn(
            "Mandrill did not deliver email: personalCode={}, templateName={}, status={}, rejectReason={}, id={}",
            person.getPersonalCode(),
            templateName,
            response.getStatus(),
            response.getRejectReason(),
            response.getId());
        return MandrillRecipientStatuses.refusesTheRecipientForGood(response)
            ? Optional.of(response)
            : Optional.empty();
      }
      return Optional.of(response);

    } catch (MandrillApiException e) {
      log.error(
          "Mandrill refused to send email: personalCode={}, templateName={}, error={}",
          person.getPersonalCode(),
          templateName,
          e.getError(),
          e);
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
    var mandrillClient = this.mandrillClient;
    if (mandrillClient == null) {
      log.warn("Mandrill not initialised, not sending system email");
      return false;
    }

    try {
      emailServiceRetryTemplate.execute(
          () -> {
            log.info("Sending system email: to={}", message.getTo());
            MandrillMessageStatus[] statuses = mandrillClient.send(message);
            MandrillRecipientStatuses.of(message, statuses).requireAcceptedByEveryMainRecipient();
            return statuses;
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
    var mandrillClient = this.mandrillClient;
    if (mandrillClient == null) {
      log.warn(
          "Mandrill not initialised, not cancelling scheduled email: mandrillMessageId={}",
          mandrillMessageId);
      return Optional.empty();
    }
    try {
      return Optional.of(mandrillClient.cancelScheduled(mandrillMessageId));
    } catch (MandrillApiException e) {
      if (e.isUnknownMessage()) {
        log.info(
            "Mandrill email already sent out?, cannot cancel: mandrillMessageId={}",
            mandrillMessageId);
      } else {
        log.error(
            "Mandrill refused to cancel scheduled email: mandrillMessageId={}, error={}",
            mandrillMessageId,
            e.getError(),
            e);
      }
    } catch (RuntimeException e) {
      log.error("Failed to cancel scheduled email: mandrillMessageId={}", mandrillMessageId, e);
    }
    return Optional.empty();
  }
}
