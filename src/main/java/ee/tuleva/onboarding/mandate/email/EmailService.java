package ee.tuleva.onboarding.mandate.email;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.config.MandateEmailConfiguration;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final MandateEmailConfiguration mandateEmailConfiguration;

    private MandrillApi mandrillApi;

    @PostConstruct
    private void initialize() {
        mandrillApi = new MandrillApi(mandateEmailConfiguration.getMandrillKey());
    }

    public void send(User user, Long mandateId, byte[] file) {
        MandrillMessage message = new MandrillMessage();
        message.setSubject("Pensionifondi avaldus");
        message.setFromEmail(mandateEmailConfiguration.getFrom());
        message.setFromName("Tuleva");
        message.setHtml(getHtml());
        message.setAutoText(true);

        message.setAttachments(getAttachements(file, user, mandateId));
        message.setTo(getRecipients(user));
        message.setPreserveRecipients(true);
        message.setTags(getTags());

        message.setImportant(true);
        message.setTrackClicks(true);

        try {
            log.info("Sending email from {} to member {} {} at {}",
                    mandateEmailConfiguration.getFrom(),
                    user.getFirstName(), user.getLastName(),
                    user.getEmail()
            );
            MandrillMessageStatus[] messageStatusReports = mandrillApi
                    .messages().send(message, false);

            log.info("Mandrill API response {}", messageStatusReports[0].getStatus()); //FIXME [0]
        } catch (MandrillApiError mandrillApiError) {
            log.error(mandrillApiError.getMandrillErrorAsJson());
            mandrillApiError.printStackTrace();
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    private String getHtml() {
        return (new StringBuilder())
                .append("Tere. <br/>")
                .append("Lisatud on pensionifondi valiku- ja/või vahetusavaldused, ")
                .append("mis on saadetud Eesti Pensioni Infosüsteemi. <br/><br/>")
                .append("Tuleva </br>")
                .toString();
    }

    private List<MandrillMessage.Recipient> getRecipients(User user) {
        ArrayList<MandrillMessage.Recipient> recipients = new ArrayList<MandrillMessage.Recipient>();

        MandrillMessage.Recipient recipient = new MandrillMessage.Recipient();

        //Member inbox
        recipient = new MandrillMessage.Recipient();
        recipient.setEmail(user.getEmail());
        recipients.add(recipient);

        //Collector inbox
        recipient = new MandrillMessage.Recipient();
        recipient.setEmail(mandateEmailConfiguration.getBcc());
        recipient.setType(MandrillMessage.Recipient.Type.BCC);
        recipients.add(recipient);

        return recipients;
    }

    private List<MandrillMessage.MessageContent> getAttachements(byte[] file, User user, Long mandateId) {
        MandrillMessage.MessageContent attachement = new MandrillMessage.MessageContent();

        attachement.setName(getNameSuffix(user)+ "_avaldus_" + mandateId + ".bdoc");
        attachement.setType("application/bdoc");
        attachement.setContent(
                Base64.getEncoder().encodeToString(file)
        );

        return Arrays.asList(attachement);
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

    private List<String> getTags() {
        return Arrays.asList("mandate");
    }

}
