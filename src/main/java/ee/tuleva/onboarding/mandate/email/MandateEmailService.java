package ee.tuleva.onboarding.mandate.email;

import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateEmailService {
  private final EmailService emailService;
  private final MandateEmailContentService emailContentService;

  public void sendMandate(
      User user,
      Mandate mandate,
      PillarSuggestion pillarSuggestion,
      UserPreferences contactDetails,
      Locale locale) {
    switch (pillarSuggestion.getOtherPillar()) {
      case 2 -> sendSecondPillarEmail(user, mandate, pillarSuggestion, locale);
      case 3 -> sendThirdPillarEmails(user, mandate, pillarSuggestion, contactDetails, locale);
      default -> throw new IllegalArgumentException(
          "Unknown pillar: " + pillarSuggestion.getOtherPillar());
    }
  }

  private void sendSecondPillarEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            "Pensionifondi avaldus",
            getSecondPillarContent(user, mandate, pillarSuggestion, locale),
            getMandateTags(pillarSuggestion),
            getMandateAttachments(mandate.getSignedFile(), user, mandate.getId()));
    emailService.send(user, mandrillMessage);
  }

  private void sendThirdPillarEmails(
      User user,
      Mandate mandate,
      PillarSuggestion pillarSuggestion,
      UserPreferences contactDetails,
      Locale locale) {
    String content =
        emailContentService.getThirdPillarPaymentDetailsHtml(
            user, pillarSuggestion, contactDetails.getPensionAccountNumber(), locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            "Sinu 3. samba tähtis info ja avalduse koopia",
            content,
            getMandateTags(pillarSuggestion),
            getMandateAttachments(mandate.getSignedFile(), user, mandate.getId()));
    emailService.send(user, mandrillMessage, new Date());
  }

  private String getSecondPillarContent(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    if (mandate.isWithdrawalCancellation()) {
      return emailContentService.getSecondPillarWithdrawalCancellationHtml(user, locale);
    }
    if (mandate.isTransferCancellation()) {
      return emailContentService.getSecondPillarTransferCancellationHtml(user, mandate, locale);
    }
    return emailContentService.getSecondPillarHtml(user, pillarSuggestion, locale);
  }

  List<String> getMandateTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate");
    tags.add("pillar_" + pillarSuggestion.getOtherPillar());
    if (pillarSuggestion.suggestMembership()) {
      tags.add("suggest_member");
    }
    if (pillarSuggestion.suggestPillar()) {
      tags.add("suggest_" + pillarSuggestion.getSuggestedPillar());
    }
    return tags;
  }

  private List<MandrillMessage.MessageContent> getMandateAttachments(
      byte[] file, User user, Long mandateId) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(getNameSuffix(user) + "_avaldus_" + mandateId + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(file));

    return singletonList(attachment);
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
