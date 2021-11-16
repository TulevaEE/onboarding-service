package ee.tuleva.onboarding.notification.email;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.config.EmailConfiguration;
import ee.tuleva.onboarding.user.User;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
      List<Recipient> to,
      String subject,
      String html,
      List<String> tags,
      List<MessageContent> attachments) {
    MandrillMessage message = new MandrillMessage();

    message.setSubject(subject);
    message.setFromEmail(emailConfiguration.getFrom());
    message.setFromName(getFromName());
    message.setHtml(html);
    message.setAutoText(true);

    message.setAttachments(attachments);
    message.setTo(to);
    message.setPreserveRecipients(true);
    message.setTags(tags);

    message.setImportant(true);
    message.setTrackClicks(true);

    return message;
  }

  public Optional<String> send(User user, MandrillMessage message) {
    return send(user, message, null);
  }

  public Optional<String> send(User user, MandrillMessage message, Instant sendAt) {
    try {
      if (mandrillApi == null) {
        log.warn(
            "Mandrill not initialised, not sending mandate email for user: userId={}",
            user.getId());
        return Optional.empty();
      }

      Date sendDate = sendAt != null ? Date.from(sendAt) : null;
      log.info(
          "Sending email to user: from={}, userId={}, sendAt={}",
          emailConfiguration.getFrom(),
          user.getId(),
          sendDate);

      MandrillMessageStatus messageStatusReport =
          mandrillApi.messages().send(message, false, null, sendDate)[0]; // FIXME [0]
      log.info("Mandrill API response {}", messageStatusReport.getStatus());
      return Optional.of(messageStatusReport.getId());

    } catch (MandrillApiError mandrillApiError) {
      log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
      return Optional.empty();
    } catch (IOException e) {
      log.error(e.getLocalizedMessage(), e);
      return Optional.empty();
    }
  }

  public List<Recipient> getRecipients(User user) {
    if (isBlank(user.getEmail())) {
      log.error("User email is missing: userId={}", user.getId());
    }

    List<Recipient> recipients = new ArrayList<>();

    // Member inbox
    Recipient member = new Recipient();
    member.setEmail(user.getEmail());
    recipients.add(member);

    // Collector inbox
    Recipient collector = new Recipient();
    collector.setEmail(emailConfiguration.getBcc());
    collector.setType(Recipient.Type.BCC);
    recipients.add(collector);

    return recipients;
  }

  private String getFromName() {
    return "Tuleva";
  }
}
