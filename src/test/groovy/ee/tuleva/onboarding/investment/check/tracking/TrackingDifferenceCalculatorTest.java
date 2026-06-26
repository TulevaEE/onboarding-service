package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_LOOKBACK_DAYS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_NET_TD_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_THRESHOLD_DAYS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.TrackingInput;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingDifferenceCalculatorTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 3);
  private static final BigDecimal BREACH_THRESHOLD = new BigDecimal("0.005");
  private static final BigDecimal MAX_DAILY_RETURN = new BigDecimal("0.5");

  @Mock private InvestmentParameterRepository parameterRepository;

  @InjectMocks private TrackingDifferenceCalculator calculator;

  @BeforeEach
  void setUp() {
    given(parameterRepository.findLatestValue(TRACKING_BREACH_THRESHOLD, CHECK_DATE))
        .willReturn(BREACH_THRESHOLD);
    given(parameterRepository.findLatestValue(TRACKING_MAX_DAILY_RETURN, CHECK_DATE))
        .willReturn(MAX_DAILY_RETURN);
  }

  @Test
  void calculatesFundDailyReturn() {
    var input = inputWithNav(new BigDecimal("10.50"), new BigDecimal("10.00"));

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().fundReturn()).isEqualByComparingTo(new BigDecimal("0.05"));
  }

  @Test
  void calculatesModelPortfolioReturn() {
    var securities =
        List.of(
            security("IE00A", new BigDecimal("0.60"), new BigDecimal("0.60"), "102", "100"),
            security("IE00B", new BigDecimal("0.40"), new BigDecimal("0.40"), "99", "100"));

    var input = inputWithSecurities(securities);

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.008"));
  }

  @Test
  void calculatesTrackingDifference() {
    var securities =
        List.of(
            security("IE00A", new BigDecimal("0.60"), new BigDecimal("0.60"), "102", "100"),
            security("IE00B", new BigDecimal("0.40"), new BigDecimal("0.40"), "99", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().trackingDifference()).isEqualByComparingTo(new BigDecimal("0.002"));
  }

  @Test
  void determinesBreachWhenAboveThreshold() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.30"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return = 0.03, model return = 0.02, TD = 0.01 > 0.005 threshold
    assertThat(result.get().breach()).isTrue();
  }

  @Test
  void noBreach() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "101", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().breach()).isFalse();
  }

  @Test
  void calculatesSecurityAttribution() {
    var securities =
        List.of(
            security("IE00A", new BigDecimal("0.60"), new BigDecimal("0.55"), "102", "100"),
            security("IE00B", new BigDecimal("0.40"), new BigDecimal("0.45"), "99", "100"));

    var input = inputWithSecurities(securities);

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    var attributions = result.get().securityAttributions();
    assertThat(attributions).hasSize(2);

    var attrA =
        attributions.stream().filter(a -> a.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(attrA.weightDifference()).isEqualByComparingTo(new BigDecimal("-0.05"));
    assertThat(attrA.contribution()).isEqualByComparingTo(new BigDecimal("-0.001"));

    var attrB =
        attributions.stream().filter(a -> a.isin().equals("IE00B")).findFirst().orElseThrow();
    assertThat(attrB.weightDifference()).isEqualByComparingTo(new BigDecimal("0.05"));
    assertThat(attrB.contribution()).isEqualByComparingTo(new BigDecimal("-0.0005"));
  }

  @Test
  void calculatesCashDrag() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("0.95"), "102", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(new BigDecimal("0.05"))
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().cashDrag()).isEqualByComparingTo(new BigDecimal("-0.001"));
  }

  @Test
  void calculatesFeeDrag() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.20"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(new BigDecimal("0.02"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().feeDrag()).isNegative();
  }

  @Test
  void calculatesResidual() {
    var securities =
        List.of(
            security("IE00A", new BigDecimal("0.60"), new BigDecimal("0.55"), "102", "100"),
            security("IE00B", new BigDecimal("0.40"), new BigDecimal("0.40"), "99", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(new BigDecimal("0.05"))
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    var td = result.get().trackingDifference();
    var attributedSum =
        result.get().securityAttributions().stream()
            .map(SecurityAttribution::contribution)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(result.get().cashDrag())
            .add(result.get().feeDrag());
    assertThat(result.get().residual()).isEqualByComparingTo(td.subtract(attributedSum));
  }

  @Test
  void navResidualIsZeroWhenFundNavMatchesBeginningOfDayHoldings() {
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.20"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(List.of(bodHolding("IE00A", new BigDecimal("1.00"), "102", "100")))
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return 0.02 == implied (1.0 sleeve fraction * 1.0 weight * 0.02 return)
    assertThat(result.get().impliedFundReturn()).isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(result.get().navResidual()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void navResidualReDilutesBySecuritiesFractionAndAppliesFeeDrag() {
    // 95% securities sleeve (5% cash) and a management fee. impliedFundReturn must re-dilute the
    // sleeve return by the securities fraction and subtract the fee drag.
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.189"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(new BigDecimal("0.05"))
            .annualFeeRate(new BigDecimal("0.0365"))
            .bodHoldings(List.of(bodHolding("IE00A", new BigDecimal("1.00"), "102", "100")))
            .bodSecuritiesFraction(new BigDecimal("0.95"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // 0.95 * (1.0 * 0.02) + (-0.0365/365) = 0.019 - 0.0001 = 0.0189
    assertThat(result.get().impliedFundReturn()).isEqualByComparingTo(new BigDecimal("0.0189"));
    // fund return = 10.189/10.00 - 1 = 0.0189 -> navResidual 0
    assertThat(result.get().navResidual()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void navResidualBreachesWhenFundNavDivergesFromHeldHoldings() {
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.30"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(List.of(bodHolding("IE00A", new BigDecimal("1.00"), "102", "100")))
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return 0.03 vs implied 0.02 -> navResidual 0.01 > 0.005 threshold
    assertThat(result.get().navResidual()).isEqualByComparingTo(new BigDecimal("0.01"));
    assertThat(result.get().navResidualBreach()).isTrue();
  }

  @Test
  void navResidualUsesRawReturnSoLargeMovesAboveCapStillReconcile() {
    // A held instrument legitimately moves +60% (above the 50% maxDailyReturn cap). The NAV used
    // this price, so the residual must reconcile against the raw return (no cap) -> navResidual 0.
    // With a cap the implied return would be zeroed, manufacturing a spurious full-size breach.
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("16.00"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "160", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(List.of(bodHolding("IE00A", new BigDecimal("1.00"), "160", "100")))
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // raw implied = 1.0 * 0.60 = 0.60; fund return = 16/10 - 1 = 0.60 -> navResidual 0
    assertThat(result.get().impliedFundReturn()).isEqualByComparingTo(new BigDecimal("0.60"));
    assertThat(result.get().navResidual()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void modelTdBreachesButNavResidualDoesNotOnTradeDay() {
    // Model already swapped to the freshly bought instrument (+0.81%); the fund still held its
    // begin-of-day portfolio (+0.23%) intraday. Fund-vs-model TD breaches, navResidual ~0.
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.023"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE000NEW",
                        new BigDecimal("1.00"),
                        new BigDecimal("1.00"),
                        "100.81",
                        "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(List.of(bodHolding("IE00OLD", new BigDecimal("1.00"), "100.23", "100")))
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().breach()).isTrue();
    assertThat(result.get().navResidualBreach()).isFalse();
    assertThat(result.get().navResidual()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void navResidualNotComputedWhenBeginningOfDayHoldingsAbsent() {
    var input = inputWithNav(new BigDecimal("10.30"), new BigDecimal("10.00"));

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().navResidualBreach()).isFalse();
    assertThat(result.get().navResidual()).isNull();
    assertThat(result.get().impliedFundReturn()).isNull();
  }

  @Test
  void navResidualNotComputedWhenBodHoldingsNullDespiteSecuritiesFraction() {
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.30"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(null)
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().impliedFundReturn()).isNull();
    assertThat(result.get().navResidual()).isNull();
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void navResidualNotComputedWhenBodHoldingsEmpty() {
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.30"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(List.of())
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().impliedFundReturn()).isNull();
    assertThat(result.get().navResidual()).isNull();
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void navResidualSkipsBeginningOfDayHoldingsWithMissingOrZeroPrices() {
    // A held instrument with a missing today price, and one with a zero anchor price, cannot be
    // priced into the implied return — both are skipped, only the priceable holding contributes.
    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(CHECK_DATE)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.20"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(
                List.of(
                    security(
                        "IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100")))
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .bodHoldings(
                List.of(
                    bodHolding("IE00A", new BigDecimal("1.00"), "102", "100"),
                    new TrackingDifferenceCalculator.BodHolding(
                        "IE00B",
                        new BigDecimal("0.50"),
                        new PriceSnapshot(null, null),
                        new PriceSnapshot(new BigDecimal("100"), null)),
                    new TrackingDifferenceCalculator.BodHolding(
                        "IE00C",
                        new BigDecimal("0.50"),
                        new PriceSnapshot(new BigDecimal("100"), null),
                        new PriceSnapshot(BigDecimal.ZERO, null)),
                    new TrackingDifferenceCalculator.BodHolding(
                        "IE00D",
                        new BigDecimal("0.50"),
                        new PriceSnapshot(new BigDecimal("100"), null),
                        new PriceSnapshot(null, null))))
            .bodSecuritiesFraction(new BigDecimal("1.00"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // Only IE00A is priceable: 1.0 sleeve fraction * 1.0 weight * 0.02 return = 0.02 == fund
    // return.
    assertThat(result.get().impliedFundReturn()).isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(result.get().navResidual()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get().navResidualBreach()).isFalse();
  }

  @Test
  void returnsEmptyWhenYesterdayNavIsZero() {
    var input = inputWithNav(new BigDecimal("10.50"), BigDecimal.ZERO);

    var result = calculator.calculate(input);

    assertThat(result).isEmpty();
  }

  @Test
  void usesRawSecurityReturnForModelTdEvenAboveMaxDailyCap() {
    // The fund NAV was valued with this price, so the model TD must reconcile against the raw
    // return. No cap is applied (price-value sanity lives upstream in FundValueIntegrityChecker);
    // the persisted benchmarkReturn/securityReturn feed the periodic attribution's compounding.
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "200", "100"));

    var input = inputWithSecurities(securities);

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // 100% raw return, model weight 1.0 -> benchmarkReturn 1.0 (not clamped to 0)
    assertThat(result.get().benchmarkReturn()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(result.get().securityAttributions().getFirst().securityReturn())
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void skipsMissingSecurityPrices() {
    var securities =
        List.of(
            security("IE00A", new BigDecimal("0.60"), new BigDecimal("0.60"), "102", "100"),
            new SecurityData(
                "IE00B",
                new BigDecimal("0.40"),
                new BigDecimal("0.40"),
                new PriceSnapshot(null, null),
                new PriceSnapshot(new BigDecimal("100"), null)));

    var input = inputWithSecurities(securities);

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    assertThat(result.get().securityAttributions()).hasSize(1);
    assertThat(result.get().benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.012"));
  }

  @Test
  void throwsWhenBreachThresholdParameterMissing() {
    given(parameterRepository.findLatestValue(TRACKING_BREACH_THRESHOLD, CHECK_DATE))
        .willThrow(
            new IllegalStateException(
                "No investment parameter found: parameter=TRACKING_BREACH_THRESHOLD"));

    var input = inputWithNav(new BigDecimal("10.10"), new BigDecimal("10.00"));

    assertThatThrownBy(() -> calculator.calculate(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("parameter=TRACKING_BREACH_THRESHOLD");
  }

  @Test
  void breachThreshold_delegatesToRepository() {
    BigDecimal value = calculator.breachThreshold(CHECK_DATE);

    assertThat(value).isEqualByComparingTo(BREACH_THRESHOLD);
  }

  private TrackingInput inputWithNav(BigDecimal todayNav, BigDecimal yesterdayNav) {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    return TrackingInput.builder()
        .fund(TUK75)
        .checkDate(CHECK_DATE)
        .checkType(MODEL_PORTFOLIO)
        .todayNav(todayNav)
        .yesterdayNav(yesterdayNav)
        .securities(securities)
        .cashWeight(BigDecimal.ZERO)
        .annualFeeRate(BigDecimal.ZERO)
        .build();
  }

  private TrackingInput inputWithSecurities(List<SecurityData> securities) {
    return TrackingInput.builder()
        .fund(TUK75)
        .checkDate(CHECK_DATE)
        .checkType(MODEL_PORTFOLIO)
        .todayNav(new BigDecimal("10.10"))
        .yesterdayNav(new BigDecimal("10.00"))
        .securities(securities)
        .cashWeight(BigDecimal.ZERO)
        .annualFeeRate(BigDecimal.ZERO)
        .build();
  }

  @Test
  void escalationLookbackDaysReadsFromParameter() {
    given(parameterRepository.findLatestValue(ESCALATION_LOOKBACK_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("10"));

    assertThat(calculator.escalationLookbackDays(CHECK_DATE)).isEqualTo(10);
  }

  @Test
  void escalationThresholdDaysReadsFromParameter() {
    given(parameterRepository.findLatestValue(ESCALATION_THRESHOLD_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("3"));

    assertThat(calculator.escalationThresholdDays(CHECK_DATE)).isEqualTo(3);
  }

  @Test
  void escalationLookbackDaysRejectsZeroOrNegative() {
    given(parameterRepository.findLatestValue(ESCALATION_LOOKBACK_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("0"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> calculator.escalationLookbackDays(CHECK_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void escalationThresholdDaysRejectsZeroOrNegative() {
    given(parameterRepository.findLatestValue(ESCALATION_THRESHOLD_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("-1"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> calculator.escalationThresholdDays(CHECK_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void escalationLookbackDaysRejectsFractionalValue() {
    given(parameterRepository.findLatestValue(ESCALATION_LOOKBACK_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("3.5"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> calculator.escalationLookbackDays(CHECK_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void escalationNetTdThresholdReadsFromParameter() {
    given(parameterRepository.findLatestValue(ESCALATION_NET_TD_THRESHOLD, CHECK_DATE))
        .willReturn(new BigDecimal("0.005"));

    assertThat(calculator.escalationNetTdThreshold(CHECK_DATE))
        .isEqualByComparingTo(new BigDecimal("0.005"));
  }

  @Test
  void escalationNetTdThresholdRejectsZeroOrNegative() {
    given(parameterRepository.findLatestValue(ESCALATION_NET_TD_THRESHOLD, CHECK_DATE))
        .willReturn(BigDecimal.ZERO);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> calculator.escalationNetTdThreshold(CHECK_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  private SecurityData security(
      String isin,
      BigDecimal modelWeight,
      BigDecimal actualWeight,
      String todayPrice,
      String yesterdayPrice) {
    return new SecurityData(
        isin,
        modelWeight,
        actualWeight,
        new PriceSnapshot(new BigDecimal(todayPrice), null),
        new PriceSnapshot(new BigDecimal(yesterdayPrice), null));
  }

  private TrackingDifferenceCalculator.BodHolding bodHolding(
      String isin, BigDecimal weight, String todayPrice, String yesterdayPrice) {
    return new TrackingDifferenceCalculator.BodHolding(
        isin,
        weight,
        new PriceSnapshot(new BigDecimal(todayPrice), null),
        new PriceSnapshot(new BigDecimal(yesterdayPrice), null));
  }
}
