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
import ee.tuleva.onboarding.config.EmailConfiguration;
import ee.tuleva.onboarding.user.User;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

  private final EmailConfiguration emailConfiguration;
  private final MandrillApi mandrillApi;

  public EmailService(
      EmailConfiguration emailConfiguration, @Autowired(required = false) MandrillApi mandrillApi) {
    this.emailConfiguration = emailConfiguration;
    this.mandrillApi = mandrillApi;
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
      User user, MandrillMessage message, String templateName) {
    return send(user, message, templateName, null);
  }

  public Optional<MandrillMessageStatus> send(
      User user, MandrillMessage message, String templateName, Instant sendAt) {
    if (mandrillApi == null) {
      log.warn(
          "Mandrill not initialised, not sending email for user: userId={}, sendAt={}, templateName={}",
          user.getId(),
          templateName,
          sendAt);
      return Optional.empty();
    }

    try {
      Date sendDate = sendAt != null ? Date.from(sendAt) : null;
      log.info(
          "Sending email to user: userId={}, sendAt={}, templateName={}",
          user.getId(),
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
      return Optional.of(response);

    } catch (MandrillApiError mandrillApiError) {
      log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
      return Optional.empty();
    } catch (IOException e) {
      log.error(e.getLocalizedMessage(), e);
      return Optional.empty();
    }
  }

  public void sendSystemEmail(MandrillMessage message) {
    if (mandrillApi == null) {
      log.warn("Mandrill not initialised, not sending system email");
      return;
    }

    try {
      log.info("Sending system email: to={}", message.getTo());
      MandrillMessageStatus response = mandrillApi.messages().send(message, false)[0];
      log.info(
          "Mandrill API response: status={}, id={}, rejectReason={}",
          response.getStatus(),
          response.getId(),
          response.getRejectReason());
    } catch (MandrillApiError mandrillApiError) {
      log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
    } catch (IOException e) {
      log.error(e.getLocalizedMessage(), e);
    }
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
}
