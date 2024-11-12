package ee.tuleva.onboarding.mandate.email;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateBatchEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final Clock clock;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;
  private final AuthenticationHolder authenticationHolder;

  public void sendMandateBatch(
      User user, MandateBatch mandateBatch, PillarSuggestion pillarSuggestion, Locale locale) {

    // TODO

    EmailType emailType = EmailType.from(mandateBatch);
    String templateName = emailType.getTemplateName(locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, mandateBatch, pillarSuggestion, locale),
            getMandateBatchTags(pillarSuggestion),
            getMandateAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), mandateBatch));
  }

  private Map<String, Object> getMergeVars(
      User user, MandateBatch mandateBatch, PillarSuggestion pillarSuggestion, Locale locale) {
    var mergeVars = new HashMap<String, Object>();
    mergeVars.put("fname", user.getFirstName());
    mergeVars.put("lname", user.getLastName());

    DateTimeFormatter dateTimeFormatter = ofPattern("dd.MM.yyyy");

    mergeVars.put("suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate());
    mergeVars.put("suggestMembership", pillarSuggestion.isSuggestMembership());
    mergeVars.put("suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar());

    return mergeVars;
  }

  private List<String> getMandateBatchTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate_batch");
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestThirdPillar()) {
      tags.add("suggest_3");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    return tags;
  }

  public List<MandrillMessage.MessageContent> getMandateAttachments(
      User user, MandateBatch mandateBatch) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(getNameSuffix(user) + "_avaldused_" + mandateBatch.getId() + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(mandateBatch.getFile()));

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
