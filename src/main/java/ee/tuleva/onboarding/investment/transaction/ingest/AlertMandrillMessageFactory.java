package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AlertMandrillMessageFactory {

  private final AlertProperties alertProperties;

  MandrillMessage create(String subject, String textBody) {
    MandrillMessage message = new MandrillMessage();
    message.setFromEmail("funds@tuleva.ee");
    message.setFromName("Tuleva");
    message.setSubject(subject);
    message.setText(textBody);
    message.setAutoHtml(true);
    message.setPreserveRecipients(true);
    message.setTo(buildRecipients());
    return message;
  }

  private List<Recipient> buildRecipients() {
    List<Recipient> recipients = new ArrayList<>();
    for (String to : alertProperties.to()) {
      Recipient r = new Recipient();
      r.setEmail(to);
      r.setType(TO);
      recipients.add(r);
    }
    for (String cc : alertProperties.cc()) {
      Recipient r = new Recipient();
      r.setEmail(cc);
      r.setType(CC);
      recipients.add(r);
    }
    return recipients;
  }
}
