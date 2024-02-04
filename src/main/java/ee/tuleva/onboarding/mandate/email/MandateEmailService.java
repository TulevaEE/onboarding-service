package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_SUGGEST_SECOND;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder;
import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final Clock clock;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;
  private final AuthenticationHolder authenticationHolder;

  public void sendMandate(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    switch (mandate.getPillar()) {
      case 2 -> sendSecondPillarEmail(user, mandate, pillarSuggestion, locale);
      case 3 -> {
        scheduleThirdPillarPaymentReminderEmail(user, mandate, locale);
        if (pillarSuggestion.isSuggestPillar()) {
          scheduleThirdPillarSuggestSecondEmail(user, mandate, pillarSuggestion, locale);
        }
      }
      default -> throw new IllegalArgumentException("Unknown pillar: " + mandate.getPillar());
    }
  }

  private void sendSecondPillarEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    EmailType emailType = EmailType.from(mandate);
    String templateName = emailType.getTemplateName(locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, mandate, pillarSuggestion, locale),
            getSecondPillarMandateTags(pillarSuggestion),
            getMandateAttachments(user, mandate));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), mandate));
  }

  private Map<String, Object> getMergeVars(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    var mergeVars = new HashMap<String, Object>();
    mergeVars.put("fname", user.getFirstName());
    mergeVars.put("lname", user.getLastName());

    DateTimeFormatter dateTimeFormatter = ofPattern("dd.MM.yyyy");
    if (mandate.isPaymentRateApplication()) {
      final var paymentRates =
          secondPillarPaymentRateService.getPaymentRates(
              authenticationHolder.getAuthenticatedPerson());

      mergeVars.put(
          "newPaymentRate",
          paymentRates
              .getPending()
              .orElseThrow(
                  () ->
                      new NoSuchElementException(
                          "No second pillar pending payment rate to send an email with")));
      mergeVars.put("oldPaymentRate", paymentRates.getCurrent());
      mergeVars.put(
          "paymentRateFulfillmentDate",
          mandateDeadlinesService
              .getDeadlines()
              .getPaymentRateFulfillmentDate()
              .format(dateTimeFormatter));
    } else {
      MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines(mandate.getCreatedDate());
      mergeVars.put(
          "transferDate", deadlines.getTransferMandateFulfillmentDate().format(dateTimeFormatter));
    }

    if (mandate.isTransferCancellation()) {
      String sourceFundIsin = mandate.getFundTransferExchanges().get(0).getSourceFundIsin();
      String sourceFundName = fundRepository.findByIsin(sourceFundIsin).getName(locale);
      mergeVars.put("sourceFundName", sourceFundName);
    }

    mergeVars.put("suggestMembership", pillarSuggestion.isSuggestMembership());
    mergeVars.put("suggestThirdPillar", pillarSuggestion.isSuggestPillar());

    return mergeVars;
  }

  private List<String> getSecondPillarMandateTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate");
    tags.add("pillar_2");
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    if (pillarSuggestion.isSuggestPillar()) {
      tags.add("suggest_3");
    }
    return tags;
  }

  private void scheduleThirdPillarPaymentReminderEmail(User user, Mandate mandate, Locale locale) {
    Instant sendAt = Instant.now(clock).plus(1, HOURS);
    EmailType emailType = EmailType.from(mandate);
    String templateName = emailType.getTemplateName(locale);

    if (emailPersistenceService.hasEmailsToday(user, emailType)) {
      log.info("Already has email today: userId={}, emailType={}", user.getId(), emailType);
      return;
    }

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName()),
            List.of("pillar_3.1", "reminder"),
            getMandateAttachments(user, mandate));

    emailService
        .send(user, message, templateName, sendAt)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user,
                    response.getId(),
                    EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE,
                    response.getStatus(),
                    mandate));
  }

  void scheduleThirdPillarSuggestSecondEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    Instant sendAt = Instant.now(clock).plus(3, DAYS);
    EmailType emailType = EmailType.from(mandate, pillarSuggestion);
    String templateName = emailType.getTemplateName(locale);

    if (emailPersistenceService.hasEmailsToday(user, emailType)) {
      log.info("Already has email today: userId={}, emailType={}", user.getId(), emailType);
      return;
    }

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName()),
            List.of("pillar_3.1", "suggest_2"),
            null);

    emailService
        .send(user, message, templateName, sendAt)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), THIRD_PILLAR_SUGGEST_SECOND, response.getStatus()));
  }

  public List<MandrillMessage.MessageContent> getMandateAttachments(User user, Mandate mandate) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(getNameSuffix(user) + "_avaldus_" + mandate.getId() + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(mandate.getSignedFile()));

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
