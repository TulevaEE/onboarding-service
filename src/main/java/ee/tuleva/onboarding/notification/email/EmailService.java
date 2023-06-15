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

  @Autowired
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
    message.setTo(List.of(new Recipient()));
    message.setPreserveRecipients(true);
    message.setTags(tags);

    message.setImportant(true);
    message.setTrackClicks(true);
    message.setTrackOpens(true);
    message.setGoogleAnalyticsDomains(List.of("tuleva.ee"));
    message.setGoogleAnalyticsCampaign(templateName);

    return message;
  }

  public Optional<String> send(User user, MandrillMessage message, String templateName) {
    return send(user, message, templateName, null);
  }

  public Optional<String> send(
      User user, MandrillMessage message, String templateName, Instant sendAt) {
    try {
      if (mandrillApi == null) {
        log.warn(
            "Mandrill not initialised, not sending mandate email for user: userId={}",
            user.getId());
        return Optional.empty();
      }

      Date sendDate = sendAt != null ? Date.from(sendAt) : null;
      log.info("Sending email to user: userId={}, sendAt={}", user.getId(), sendDate);

      MandrillMessageStatus messageStatusReport =
          mandrillApi.messages()
              .sendTemplate(templateName, Map.of(), message, false, null, sendDate)[0];
      log.info(
          "Mandrill API response: status={}, id={}",
          messageStatusReport.getStatus(),
          messageStatusReport.getId());
      return Optional.of(messageStatusReport.getId());

    } catch (MandrillApiError mandrillApiError) {
      log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
      return Optional.empty();
    } catch (IOException e) {
      log.error(e.getLocalizedMessage(), e);
      return Optional.empty();
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
