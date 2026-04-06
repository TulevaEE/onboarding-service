package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.TrackingInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackingDifferenceCalculatorTest {

  private final TrackingDifferenceCalculator calculator = new TrackingDifferenceCalculator();

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
    // model return = 0.60 * 0.02 + 0.40 * (-0.01) = 0.012 - 0.004 = 0.008
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
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return = 0.01, model return = 0.008, TD = 0.002
    assertThat(result.get().trackingDifference()).isEqualByComparingTo(new BigDecimal("0.002"));
  }

  @Test
  void determinesBreachWhenAboveThreshold() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.30"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return = 0.03, model return = 0.02, TD = 0.01 > 0.001
    assertThat(result.get().breach()).isTrue();
  }

  @Test
  void noBreach() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "101", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fund return = 0.01, model return = 0.01, TD = 0
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
    // weight diff = 0.55 - 0.60 = -0.05, return = 0.02, contribution = -0.05 * 0.02 = -0.001
    assertThat(attrA.weightDifference()).isEqualByComparingTo(new BigDecimal("-0.05"));
    assertThat(attrA.contribution()).isEqualByComparingTo(new BigDecimal("-0.001"));

    var attrB =
        attributions.stream().filter(a -> a.isin().equals("IE00B")).findFirst().orElseThrow();
    // weight diff = 0.45 - 0.40 = 0.05, return = -0.01, contribution = 0.05 * (-0.01) = -0.0005
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
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.10"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(new BigDecimal("0.05"))
            .annualFeeRate(BigDecimal.ZERO)
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // model return = 1.00 * 0.02 = 0.02
    // cash drag = -(0.05) * 0.02 = -0.001
    assertThat(result.get().cashDrag()).isEqualByComparingTo(new BigDecimal("-0.001"));
  }

  @Test
  void calculatesFeeDrag() {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    var input =
        TrackingInput.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .todayNav(new BigDecimal("10.20"))
            .yesterdayNav(new BigDecimal("10.00"))
            .securities(securities)
            .cashWeight(BigDecimal.ZERO)
            .annualFeeRate(new BigDecimal("0.00365"))
            .build();

    var result = calculator.calculate(input);

    assertThat(result).isPresent();
    // fee drag = -(0.00365 / 365) = -0.00001
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
            .checkDate(LocalDate.of(2026, 4, 3))
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
    // residual = TD - attributed
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
    // 100% return > 25% threshold, clamped to 0
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
    // model return only from IE00A: 0.60 * 0.02 = 0.012
    assertThat(result.get().benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.012"));
  }

  private TrackingInput inputWithNav(BigDecimal todayNav, BigDecimal yesterdayNav) {
    var securities =
        List.of(security("IE00A", new BigDecimal("1.00"), new BigDecimal("1.00"), "102", "100"));

    return TrackingInput.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 4, 3))
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
        .checkDate(LocalDate.of(2026, 4, 3))
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
