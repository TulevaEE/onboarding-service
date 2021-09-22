package ee.tuleva.onboarding.mandate.email;

import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
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
  private final Clock clock;

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
    sendThirdPillarPaymentDetailsEmail(user, mandate, contactDetails, locale);
    sendThirdPillarSuggestSecondEmail(user, pillarSuggestion, locale);
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

  private void sendThirdPillarPaymentDetailsEmail(
      User user, Mandate mandate, UserPreferences contactDetails, Locale locale) {
    String content =
        emailContentService.getThirdPillarPaymentDetailsHtml(
            user, contactDetails.getPensionAccountNumber(), locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            "Sinu 3. samba tähtis info ja avalduse koopia",
            content,
            List.of("mandate"),
            getMandateAttachments(mandate.getSignedFile(), user, mandate.getId()));
    emailService.send(user, mandrillMessage);
  }

  private void sendThirdPillarSuggestSecondEmail(
      User user, PillarSuggestion pillarSuggestion, Locale locale) {
    if (!pillarSuggestion.suggestPillar()) return;

    List<Recipient> recipients = emailService.getRecipients(user);
    String subject = "Vaata oma teine sammas üle!";
    String content = emailContentService.getThirdPillarSuggestSecondHtml(user, locale);
    List<String> tags = List.of("suggest_2");
    Instant sendAt = Instant.now(clock).plus(3, ChronoUnit.DAYS);

    MandrillMessage message =
        emailService.newMandrillMessage(recipients, subject, content, tags, null);
    emailService.send(user, message, sendAt);
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
