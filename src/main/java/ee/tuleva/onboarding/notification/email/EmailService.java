package ee.tuleva.onboarding.notification.email;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.config.EmailConfiguration;
import ee.tuleva.onboarding.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class EmailService {

    private final EmailConfiguration emailConfiguration;
    private final EmailContentService emailContentService;
    private final MandrillApi mandrillApi;

    @Autowired
    public EmailService(EmailConfiguration emailConfiguration,
                        EmailContentService emailContentService,
                        @Autowired(required = false)
                                MandrillApi mandrillApi) {
        this.emailConfiguration = emailConfiguration;
        this.emailContentService = emailContentService;
        this.mandrillApi = mandrillApi;
    }


    public void sendMandate(User user, Long mandateId, byte[] file) {

        if (mandrillApi == null) {
            log.warn("Mandrill not initialised, not sending mandate email for user {} and mandate {}.", user.getId(), mandateId);
            return;
        }

        MandrillMessage message = newMandrillMessage(
                getRecipients(user), getMandateEmailSubject(),
                emailContentService.getMandateEmailHtml(), getMandateTags(),
                getMandateAttachements(file, user, mandateId));

        send(user, message);
    }

    public void sendMemberNumber(User user) {

        if (mandrillApi == null) {
            log.warn("Mandrill not initialised, not sending member number email for userId {}, member #", user.getId(), user.getMemberOrThrow().getMemberNumber());
            return;
        }

        log.info("Sending member number email to user: {}", user);

        MandrillMessage message = newMandrillMessage(
                getRecipients(user), getMemberNumberEmailSubject(),
                emailContentService.getMembershipEmailHtml(user),
                getMemberNumberTags(), null);

        send(user, message);
    }

    private MandrillMessage newMandrillMessage(List<Recipient> to, String subject, String html, List<String> tags, List<MessageContent> attachments) {
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


    private String getFromName() {
        return "Tuleva";
    }

    private String getMandateEmailSubject() {
        return "Pensionifondi avaldus";
    }

    private String getMemberNumberEmailSubject() {
        return "Tuleva liikmetunnistus";
    }

    private void send(User user, MandrillMessage message) {
        try {
            log.info("Sending email from {} to member {} {} at {}",
                    emailConfiguration.getFrom(),
                    user.getFirstName(), user.getLastName(),
                    user.getEmail()
            );
            MandrillMessageStatus[] messageStatusReports = mandrillApi
                    .messages().send(message, false);

            log.info("Mandrill API response {}", messageStatusReports[0].getStatus()); //FIXME [0]
        } catch (MandrillApiError mandrillApiError) {
            log.error(mandrillApiError.getMandrillErrorAsJson(), mandrillApiError);
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    private List<Recipient> getRecipients(User user) {
        ArrayList<Recipient> recipients = new ArrayList<>();

        //Member inbox
        Recipient member = new Recipient();
        member.setEmail(user.getEmail());
        recipients.add(member);

        //Collector inbox
        Recipient collector = new Recipient();
        collector.setEmail(emailConfiguration.getBcc());
        collector.setType(Recipient.Type.BCC);
        recipients.add(collector);

        return recipients;
    }

    private List<MessageContent> getMandateAttachements(byte[] file, User user, Long mandateId) {
        MessageContent attachement = new MessageContent();

        attachement.setName(getNameSuffix(user) + "_avaldus_" + mandateId + ".bdoc");
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

    private List<String> getMandateTags() {
        return Arrays.asList("mandate");
    }

    private List<String> getMemberNumberTags() {
        return Arrays.asList("memberNumber");
    }

}
