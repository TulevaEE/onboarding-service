package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.*;
import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.MandateType.PAYMENT_RATE_CHANGE;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;
import static ee.tuleva.onboarding.mandate.MandateType.SELECTION;
import static ee.tuleva.onboarding.notification.email.EmailType.BATCH_FAILED;
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_REJOIN;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.nudge.FundFees;
import ee.tuleva.onboarding.nudge.PensionRegistry;
import ee.tuleva.onboarding.nudge.PensionRegistrySnapshot;
import ee.tuleva.onboarding.pillar.Pillar;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateBatchEmailService {

  private static final int REJOINERS_LEFT_BEFORE_HIGHER_RATES_EXISTED_SO_RATE_ON_FILE_IS_TWO = 2;
  private static final int MAX_PAYMENT_RATE = 6;

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final MandateProcessorService mandateProcessor;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final FundRepository fundRepository;
  private final PensionRegistry pensionRegistry;

  public void sendMandateBatch(User user, MandateBatch mandateBatch, Locale locale) {
    Long mandateBatchId = mandateBatchIdOf(mandateBatch);

    if (emailPersistenceService.hasEmailsForMandateBatch(mandateBatchId)) {
      log.warn("Skipping mandatebatch (id={}) email as email already present", mandateBatchId);
      return;
    }

    log.info("Sending mandatebatch (id={}) email", mandateBatchId);

    EmailType emailType = MandateEmailType.emailTypeFor(mandateBatch);
    String templateName = emailType.getTemplateName(locale);
    Map<String, Object> mergeVars = getMergeVars(user, mandateBatch);
    if (emailType == SECOND_PILLAR_REJOIN) {
      mergeVars.putAll(getRejoinMergeVars(mandateBatch, locale));
      mergeVars.putAll(getRejoinFundMergeVars(user, mandateBatch, locale));
      mergeVars.putAll(getRejoinPaymentRateMergeVars(mandateBatch));
    }
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            mergeVars,
            getMandateBatchTags(mandateBatch),
            getAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.saveWithMandateBatch(
                    user, response.getId(), emailType, response.getStatus(), mandateBatchId));
  }

  public void sendMandateBatchFailedEmail(User user, MandateBatch mandateBatch, Locale locale) {
    Long mandateBatchId = mandateBatchIdOf(mandateBatch);

    if (emailPersistenceService.hasEmailsForMandateBatch(mandateBatchId)) {
      log.warn(
          "Skipping mandatebatch (id={}) failed email as email already present", mandateBatchId);
      return;
    }

    log.info("Sending failed mandatebatch (id={}) email", mandateBatchId);

    var emailType = BATCH_FAILED;
    String templateName = emailType.getTemplateName(locale);

    Map<String, Object> mergeVars = getMergeVars(user, mandateBatch);
    mergeVars.putAll(getFailedBatchMergeVars(mandateBatch));

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            mergeVars,
            getMandateBatchTags(mandateBatch),
            getAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.saveWithMandateBatch(
                    user, response.getId(), emailType, response.getStatus(), mandateBatchId));
  }

  private Long mandateBatchIdOf(MandateBatch mandateBatch) {
    return requireNonNull(mandateBatch.getId(), "Mandate batch is not yet persisted");
  }

  private Map<String, Object> getMergeVars(User user, MandateBatch batch) {
    var map = new HashMap<String, Object>();
    map.putAll(getNameMergeVars(user));
    map.putAll(getWithdrawalMandateMergeVars(batch));

    return map;
  }

  private Map<String, Object> getRejoinMergeVars(MandateBatch mandateBatch, Locale locale) {
    var deadlines =
        mandateDeadlinesService.getDeadlines(
            dateSentToPensionikeskus(rejoinMandateOf(mandateBatch)));
    var dateFormat = dateFormatIn(locale);
    return Map.of(
        "contributionStartDate",
        deadlines.getSecondPillarRejoinContributionStartDate().format(dateFormat),
        "cancellationDeadline",
        deadlines.getSecondPillarRejoinDeadlineDate().format(dateFormat),
        "paymentRateChange",
        contains(mandateBatch, PAYMENT_RATE_CHANGE),
        "fundSelection",
        contains(mandateBatch, SELECTION));
  }

  private Map<String, Object> getRejoinFundMergeVars(
      User user, MandateBatch mandateBatch, Locale locale) {
    var mergeVars = new HashMap<String, Object>();
    var selectedFund = selectedFundOf(mandateBatch);
    var selectedOtherFund = selectedFund.filter(fund -> !fund.isOwnFund());
    var highFee = selectedOtherFund.map(Fund::getOngoingChargesFigure).filter(FundFees::isHigh);
    mergeVars.put("selectedTulevaStockFund", selectedFund.filter(isTulevaFund(TUK75)).isPresent());
    mergeVars.put("selectedTulevaBondFund", selectedFund.filter(isTulevaFund(TUK00)).isPresent());
    mergeVars.put("selectedOtherFund", selectedOtherFund.isPresent());
    mergeVars.put("selectedHighFeeFund", highFee.isPresent());
    mergeVars.put("keptTulevaFund", selectedFund.isEmpty() && contributionsOnFileGoToTuleva(user));
    selectedOtherFund.ifPresent(fund -> mergeVars.put("selectedFundName", fund.getName(locale)));
    highFee.ifPresent(fee -> mergeVars.put("selectedFundFee", percentIn(locale, fee)));
    return mergeVars;
  }

  private boolean contributionsOnFileGoToTuleva(User user) {
    return pensionRegistry
        .snapshotFor(user.getPersonalCode())
        .filter(PensionRegistrySnapshot::secondPillarContributionsToTuleva)
        .isPresent();
  }

  private Optional<Fund> selectedFundOf(MandateBatch mandateBatch) {
    return mandateBatch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == SELECTION)
        .flatMap(mandate -> mandate.getFutureContributionFundIsin().stream())
        .findFirst()
        .map(this::fundOf);
  }

  private Fund fundOf(String isin) {
    Fund fund = fundRepository.findByIsin(isin);
    if (fund == null) {
      throw new IllegalStateException("Rejoin selection fund not found: isin=" + isin);
    }
    return fund;
  }

  private static Predicate<Fund> isTulevaFund(TulevaFund tulevaFund) {
    return fund -> tulevaFund.getIsin().equals(fund.getIsin());
  }

  private static String percentIn(Locale locale, BigDecimal fraction) {
    String percent =
        fraction.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
    return "et".equals(locale.getLanguage()) ? percent.replace('.', ',') : percent;
  }

  private static Map<String, Object> getRejoinPaymentRateMergeVars(MandateBatch mandateBatch) {
    int paymentRate =
        mandateBatch.getMandates().stream()
            .filter(mandate -> mandate.getMandateType() == PAYMENT_RATE_CHANGE)
            .map(Mandate::getPaymentRate)
            .filter(Objects::nonNull)
            .map(BigDecimal::intValueExact)
            .findFirst()
            .orElse(REJOINERS_LEFT_BEFORE_HIGHER_RATES_EXISTED_SO_RATE_ON_FILE_IS_TWO);
    return Map.of("paymentRate", paymentRate, "maxPaymentRate", paymentRate == MAX_PAYMENT_RATE);
  }

  private static boolean contains(MandateBatch mandateBatch, MandateType mandateType) {
    return mandateBatch.getMandates().stream()
        .anyMatch(mandate -> mandate.getMandateType() == mandateType);
  }

  private static Mandate rejoinMandateOf(MandateBatch mandateBatch) {
    return mandateBatch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == REJOIN)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Rejoin batch has no rejoin mandate: mandateBatchId=" + mandateBatch.getId()));
  }

  private static Instant dateSentToPensionikeskus(Mandate rejoin) {
    return requireNonNull(
        rejoin.getCreatedDate(),
        "Rejoin mandate has no document date: mandateId=" + rejoin.getId());
  }

  private static DateTimeFormatter dateFormatIn(Locale locale) {
    return "et".equals(locale.getLanguage())
        ? DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.of("et"))
        : DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
  }

  private Map<String, Object> getFailedBatchMergeVars(MandateBatch mandateBatch) {
    var map = new HashMap<String, Object>();

    var mandates = mandateBatch.getMandates();

    int failedMandateCount =
        mandates.stream()
            .filter(mandate -> !mandateProcessor.getErrors(mandate).getErrors().isEmpty())
            .toList()
            .size();

    int succesfulMandateCount = mandates.size() - failedMandateCount;

    map.put("failedMandateCount", failedMandateCount);
    map.put("successfulMandateCount", succesfulMandateCount);
    map.put("totalMandateCount", mandates.size());

    return map;
  }

  private Map<String, Object> getWithdrawalMandateMergeVars(MandateBatch batch) {
    var map = new HashMap<String, Object>();

    var fundPensionMandatePillars = getFundPensionMandatePillars(batch);
    var partialWithdrawalMandatePillars = getPartialWithdrawalMandatesPillars(batch);

    return Map.of(
        "fundPensionSecondPillar", fundPensionMandatePillars.contains(SECOND),
        "fundPensionThirdPillar", fundPensionMandatePillars.contains(THIRD),
        "partialWithdrawalSecondPillar", partialWithdrawalMandatePillars.contains(SECOND),
        "partialWithdrawalThirdPillar", partialWithdrawalMandatePillars.contains(THIRD));
  }

  private Set<Pillar> getFundPensionMandatePillars(MandateBatch batch) {
    return batch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == FUND_PENSION_OPENING)
        .map(
            mandate ->
                ((FundPensionOpeningMandateDetails) mandate.getMandateDto().getDetails())
                    .getPillar())
        .collect(Collectors.toSet());
  }

  private Set<Pillar> getPartialWithdrawalMandatesPillars(MandateBatch batch) {
    return batch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == PARTIAL_WITHDRAWAL)
        .map(
            mandate ->
                ((PartialWithdrawalMandateDetails) mandate.getMandateDto().getDetails())
                    .getPillar())
        .collect(Collectors.toSet());
  }

  private List<String> getMandateBatchTags(MandateBatch batch) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate_batch");

    var fundPensionPillars = getFundPensionMandatePillars(batch);
    var partialWithdrawalPillars = getPartialWithdrawalMandatesPillars(batch);

    var allPillars =
        concat(fundPensionPillars.stream(), partialWithdrawalPillars.stream())
            .collect(Collectors.toSet());

    if (allPillars.contains(SECOND)) {
      tags.add("pillar_2");
    }

    if (allPillars.contains(THIRD)) {
      tags.add("pillar_3");
    }

    if (!fundPensionPillars.isEmpty()) {
      tags.add("fund_pension_opening");
    }

    if (!partialWithdrawalPillars.isEmpty()) {
      tags.add("partial_withdrawal");
    }

    return tags;
  }
}
