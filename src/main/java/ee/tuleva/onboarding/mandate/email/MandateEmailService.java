package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.*;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_SUGGEST_SECOND;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Objects.requireNonNull;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
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
  private final SavingsFundFees savingsFundFees;

  public void sendMandate(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    if (emailPersistenceService.hasEmailsForMandate(mandate.getId())) {
      log.warn("Skipping mandate (id={}) email as email already present", mandate.getId());
      return;
    }

    switch (mandate.getPillar()) {
      case 2 -> sendSecondPillarEmail(user, mandate, pillarSuggestion, locale);
      case 3 -> {
        scheduleThirdPillarPaymentReminderEmail(user, mandate, locale);
        if (pillarSuggestion.isSuggestSecondPillar()) {
          scheduleThirdPillarSuggestSecondEmail(user, mandate, pillarSuggestion, locale);
        }
      }
      default -> throw new IllegalArgumentException("Unknown pillar: " + mandate.getPillar());
    }
  }

  private void sendSecondPillarEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    EmailType emailType = MandateEmailType.emailTypeFor(mandate);
    String templateName = emailType.getTemplateName(locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, mandate, pillarSuggestion, locale),
            getSecondPillarMandateTags(pillarSuggestion),
            getAttachments(user, mandate));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.saveWithMandate(
                    user, response.getId(), emailType, response.getStatus(), mandate.getId()));
  }

  private Map<String, Object> getMergeVars(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    var mergeVars = new HashMap<String, Object>();
    mergeVars.putAll(getNameMergeVars(user));

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

      // Add decreased/increased flags for template logic
      Integer newRate = paymentRates.getPending().orElseThrow();
      Integer oldRate =
          requireNonNull(
              paymentRates.getCurrent(),
              "Missing current second pillar payment rate: personalCode=" + user.getPersonalCode());
      boolean decreased = isPaymentRateDecreased(oldRate, newRate);
      mergeVars.put("decreased", decreased);
      mergeVars.put("increased", !decreased);

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
      mergeVars.put("hasFundSelection", mandate.getFutureContributionFundIsin().isPresent());
      mergeVars.put("hasFundTransfer", !mandate.getFundTransferExchangesBySourceIsin().isEmpty());
      mergeVars.putAll(selectedFundMergeVars(user, mandate, locale));
    }

    if (mandate.isTransferCancellation()) {
      List<FundTransferExchange> fundTransferExchanges =
          requireNonNull(
              mandate.getFundTransferExchanges(),
              "Missing fund transfer exchanges for cancellation: mandateId=" + mandate.getId());
      String sourceFundIsin = fundTransferExchanges.get(0).getSourceFundIsin();
      Fund sourceFund =
          requireNonNull(
              fundRepository.findByIsin(sourceFundIsin), "Fund not found: isin=" + sourceFundIsin);
      mergeVars.put("sourceFundName", sourceFund.getName(locale));
    }

    mergeVars.putAll(
        getPillarSuggestionMergeVars(
            pillarSuggestion, savingsFundFees.ongoingChargesPercent(locale)));
    return mergeVars;
  }

  private Map<String, Object> selectedFundMergeVars(User user, Mandate mandate, Locale locale) {
    List<Fund> selectedFunds =
        Stream.concat(
                mandate.getFutureContributionFundIsin().stream(),
                mandate.getFundTransferExchangesBySourceIsin().values().stream()
                    .flatMap(List::stream)
                    .map(FundTransferExchange::getTargetFundIsin))
            .filter(Objects::nonNull)
            .distinct()
            .map(fundRepository::findByIsin)
            .filter(Objects::nonNull)
            .toList();
    boolean selectedTulevaFund = selectedFunds.stream().anyMatch(Fund::isOwnFund);
    var mergeVars = new HashMap<String, Object>();
    mergeVars.put("selectedTulevaFund", selectedTulevaFund);
    BigDecimal conservativeEquityShare = new BigDecimal("0.25");
    boolean youngInConservativeFund =
        user.getAge() < 55
            && !selectedFunds.isEmpty()
            && selectedFunds.stream()
                .anyMatch(
                    fund ->
                        fund.getEquityShare() != null
                            && fund.getEquityShare().compareTo(conservativeEquityShare) < 0);
    mergeVars.put("selectedConservativeFund", youngInConservativeFund);
    BigDecimal highFeeThreshold = new BigDecimal("0.003");
    BigDecimal highestFee =
        selectedFunds.stream()
            .filter(fund -> !fund.isOwnFund())
            .map(Fund::getOngoingChargesFigure)
            .filter(Objects::nonNull)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    boolean selectedHighFeeFund = highestFee.compareTo(highFeeThreshold) > 0;
    mergeVars.put("selectedHighFeeFund", selectedHighFeeFund);
    if (selectedHighFeeFund) {
      String fee =
          highestFee.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
      mergeVars.put(
          "selectedFundFee", "et".equals(locale.getLanguage()) ? fee.replace('.', ',') : fee);
    }
    return mergeVars;
  }

  private List<String> getSecondPillarMandateTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate");
    tags.add("pillar_2");
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestThirdPillar()) {
      tags.add("suggest_3");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }

    pillarSuggestion.renderedNudgeTag().ifPresent(tags::add);
    return tags;
  }

  private Map<String, Object> getThirdPillarReminderMergeVars(User user, Mandate mandate) {
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(user));
    mergeVars.put("hasFundTransfer", !mandate.getFundTransferExchangesBySourceIsin().isEmpty());
    return mergeVars;
  }

  private void scheduleThirdPillarPaymentReminderEmail(User user, Mandate mandate, Locale locale) {
    Instant sendAt = Instant.now(clock).plus(1, HOURS);
    EmailType emailType = MandateEmailType.emailTypeFor(mandate);
    String templateName = emailType.getTemplateName(locale);

    if (hasEmailsToday(user, emailType, mandate)) {
      log.info(
          "Already has email today: personalCode={}, emailType={}, mandateId={}",
          user.getPersonalCode(),
          emailType,
          mandate.getId());
      return;
    }

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getThirdPillarReminderMergeVars(user, mandate),
            List.of("pillar_3.1", "reminder"),
            getAttachments(user, mandate));

    emailService
        .send(user, message, templateName, sendAt)
        .ifPresent(
            response ->
                emailPersistenceService.saveWithMandate(
                    user,
                    response.getId(),
                    EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE,
                    response.getStatus(),
                    mandate.getId()));
  }

  void scheduleThirdPillarSuggestSecondEmail(
      User user, Mandate mandate, PillarSuggestion pillarSuggestion, Locale locale) {
    Instant sendAt = Instant.now(clock).plus(3, DAYS);
    EmailType emailType = MandateEmailType.emailTypeFor(mandate, pillarSuggestion);
    String templateName = emailType.getTemplateName(locale);

    if (hasEmailsToday(user, emailType, mandate)) {
      log.info(
          "Already has email today: personalCode={}, emailType={}, mandateId={}",
          user.getPersonalCode(),
          emailType,
          mandate.getId());
      return;
    }

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getNameMergeVars(user),
            List.of("pillar_3.1", "suggest_2"),
            null);

    emailService
        .send(user, message, templateName, sendAt)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), THIRD_PILLAR_SUGGEST_SECOND, response.getStatus()));
  }

  boolean isPaymentRateDecreased(Integer oldRate, Integer newRate) {
    return newRate == 2 || newRate < oldRate;
  }

  private boolean hasEmailsToday(Person person, EmailType emailType, Mandate mandate) {
    MandateBatch mandateBatch = mandate.getMandateBatch();
    if (mandateBatch != null) {
      return emailPersistenceService.hasMandateBatchEmailsToday(
          person, emailType, mandateBatch.getId());
    }
    return emailPersistenceService.hasMandateEmailsToday(person, emailType, mandate.getId());
  }
}
