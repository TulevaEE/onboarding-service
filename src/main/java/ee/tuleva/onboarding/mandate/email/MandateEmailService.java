package ee.tuleva.onboarding.mandate.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.event.MandateApplicationEvent;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class MandateEmailService {
    private final EmailService emailService;
    private final MandateEmailContentService emailContentService;

    @Autowired
    public MandateEmailService(EmailService emailService,
                               MandateEmailContentService emailContentService) {
        this.emailService = emailService;
        this.emailContentService = emailContentService;
    }

    @Async
    @EventListener(condition = "#event.name eq 'SECOND_PILLAR_MANDATE_CREATED'")
    public void onSecondPillarMandateCreatedEvent(MandateApplicationEvent event) {
        this.sendSecondPillarMandate(event.getUser(), event.getMandateId(), event.getSignedFile());
    }

    @Async
    @EventListener(condition = "#event.name eq 'THIRD_PILLAR_MANDATE_CREATED'")
    public void onThirdPillarMandateCreatedEvent(MandateApplicationEvent event) {
        this.sendThirdPillarMandate(event.getUser(), event.getMandateId(), event.getSignedFile());
    }

    public void sendSecondPillarMandate(User user, Long mandateId, byte[] file) {
        MandrillMessage message = emailService.newMandrillMessage(
            emailService.getRecipients(user), getMandateEmailSubject(),
            emailContentService.getSecondPillarHtml(), getMandateTags(),
            getMandateAttachements(file, user, mandateId));

        if(message == null) {
            log.warn(
                "Failed to create mandrill message, not sending mandate email for user {} and second pillar mandate {}.",
                user.getId(),
                mandateId
            );
            return;
        }

        emailService.send(user, message);
    }

    public void sendThirdPillarMandate(User user, Long mandateId, byte[] file) {
        MandrillMessage message = emailService.newMandrillMessage(
            emailService.getRecipients(user), getMandateEmailSubject(),
            emailContentService.getThirdPillarHtml(), getMandateTags(),
            getMandateAttachements(file, user, mandateId));

        if(message == null) {
            log.warn(
                "Failed to create mandrill message, not sending mandate email for user {} and third pillar mandate {}.",
                user.getId(),
                mandateId
            );
            return;
        }

        emailService.send(user, message);
    }

    private String getMandateEmailSubject() {
        return "Pensionifondi avaldus";
    }

    private List<String> getMandateTags() {
        return Arrays.asList("mandate");
    }

    private List<MandrillMessage.MessageContent> getMandateAttachements(byte[] file, User user, Long mandateId) {
        MandrillMessage.MessageContent attachement = new MandrillMessage.MessageContent();

        attachement.setName(user.getNameSuffix() + "_avaldus_" + mandateId + ".bdoc");
        attachement.setType("application/bdoc");
        attachement.setContent(
            Base64.getEncoder().encodeToString(file)
        );

        return Arrays.asList(attachement);
    }
}
