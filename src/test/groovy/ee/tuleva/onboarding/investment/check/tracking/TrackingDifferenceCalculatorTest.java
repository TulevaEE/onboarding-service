package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
  void returnsEmptyWhenYesterdayNavIsZero() {
    var input = inputWithNav(new BigDecimal("10.50"), BigDecimal.ZERO);

    var result = calculator.calculate(input);

    assertThat(result).isEmpty();
  }

  @Test
  void clampsAnomalousSecurityReturnToZero() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "200", "100"));

    var input = inputWithSecurities(securities);

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // 100% return > 50% threshold, clamped to 0
    assertThat(result.get().benchmarkReturn()).isEqualByComparingTo(BigDecimal.ZERO);
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
                null,
                new BigDecimal("100")));

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
        new BigDecimal(todayPrice),
        new BigDecimal(yesterdayPrice));
  }
}
