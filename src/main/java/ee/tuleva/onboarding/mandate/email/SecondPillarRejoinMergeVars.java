package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.MandateType.PAYMENT_RATE_CHANGE;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;
import static ee.tuleva.onboarding.mandate.MandateType.SELECTION;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.nudge.FundFees;
import ee.tuleva.onboarding.nudge.PensionRegistry;
import ee.tuleva.onboarding.nudge.PensionRegistrySnapshot;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SecondPillarRejoinMergeVars {

  private static final int REJOINERS_LEFT_BEFORE_HIGHER_RATES_EXISTED_SO_RATE_ON_FILE_IS_TWO = 2;
  private static final int MAX_PAYMENT_RATE = 6;

  private final MandateDeadlinesService mandateDeadlinesService;
  private final FundRepository fundRepository;
  private final PensionRegistry pensionRegistry;

  Map<String, Object> of(User user, MandateBatch mandateBatch, Locale locale) {
    var mergeVars = new HashMap<String, Object>();
    mergeVars.putAll(getRejoinMergeVars(mandateBatch, locale));
    mergeVars.putAll(getRejoinFundMergeVars(user, mandateBatch, locale));
    mergeVars.putAll(getRejoinPaymentRateMergeVars(mandateBatch));
    return mergeVars;
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
}
