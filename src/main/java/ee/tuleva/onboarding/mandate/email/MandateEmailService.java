package ee.tuleva.onboarding.mandate.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.util.Collections.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateEmailService {
    private final EmailService emailService;
    private final MandateEmailContentService emailContentService;

    public void sendSecondPillarMandate(User user, Long mandateId, byte[] file, ConversionResponse conversion,
                                        UserPreferences contactDetails, Locale locale) {
        SecondPillarSuggestion pillarSuggestion = new SecondPillarSuggestion(user, contactDetails, conversion);

        MandrillMessage message = emailService.newMandrillMessage(
            emailService.getRecipients(user),
            getMandateEmailSubject(),
            emailContentService.getSecondPillarHtml(user, pillarSuggestion, locale),
            getMandateTags(pillarSuggestion),
            getMandateAttachements(file, user, mandateId));

        if (message == null) {
            log.warn(
                "Failed to create mandrill message, not sending mandate email for user {} and second pillar mandate {}.",
                user.getId(),
                mandateId
            );
            return;
        }

        emailService.send(user, message);
    }

    public void sendThirdPillarMandate(User user, Long mandateId, byte[] file, ConversionResponse conversion,
                                       UserPreferences contactDetails, Locale locale) {
        ThirdPillarSuggestion pillarSuggestion = new ThirdPillarSuggestion(user, contactDetails, conversion);

        MandrillMessage message = emailService.newMandrillMessage(
            emailService.getRecipients(user),
            getMandateEmailSubject(),
            emailContentService.getThirdPillarHtml(user, pillarSuggestion, contactDetails.getPensionAccountNumber(), locale),
            getMandateTags(pillarSuggestion),
            getMandateAttachements(file, user, mandateId)
        );

        if (message == null) {
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

    private List<String> getMandateTags(PillarSuggestion pillarSuggestion) {
        StringBuilder tag = new StringBuilder("mandate");
        if (pillarSuggestion.suggestMembership()) {
            tag.append("_suggest_member");
        }
        if (pillarSuggestion.suggestOtherPillar()) {
            tag.append("_suggest_").append(pillarSuggestion.getPillar());
        }
        return singletonList(tag.toString());
    }

    private List<MandrillMessage.MessageContent> getMandateAttachements(byte[] file, User user, Long mandateId) {
        MandrillMessage.MessageContent attachement = new MandrillMessage.MessageContent();

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
}
