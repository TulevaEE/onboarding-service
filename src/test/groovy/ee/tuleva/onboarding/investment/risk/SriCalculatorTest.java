package ee.tuleva.onboarding.investment.risk;

import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SriCalculatorTest {

  private static final String KEY = "MSCI_ACWI";
  private static final double Z = 1.95996398454005;

  private final SriCalculator calculator = new SriCalculator();

  @Test
  void reproducesTheRtsAnnexTwoFormulaForAKnownReturnSeries() {
    var returns = deterministicReturns(1400);
    var prices = pricesFrom(returns, LocalDate.of(2019, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate);

    assertThat(points).hasSize(1);
    var point = points.getFirst();
    var windowReturns = returnsInWindow(prices, evalDate);
    assertThat(point.observationCount()).isEqualTo(windowReturns.size());
    assertThat(point.volatility().doubleValue())
        .isCloseTo(expectedVev(windowReturns), within(1e-9));
    assertThat(point.riskClass()).isEqualTo(RiskClassBucket.mrmClass(point.volatility()));
  }

  @Test
  void anEvaluationDateWithOnlyOneReturnBehindItYieldsNoReferencePoint() {
    var prices = pricesForKey(KEY, LocalDate.of(2024, 1, 1), 100.0, 4);
    var firstReturnDate = prices.get(1).date();

    var points = calculator.calculate(prices, firstReturnDate, prices.getLast().date());

    assertThat(points.stream().map(ReferencePoint::date)).doesNotContain(firstReturnDate);
    assertThat(points.getFirst().date()).isEqualTo(prices.get(2).date());
  }

  @Test
  void noReturnIsComputedBetweenTwoDifferentSources() {
    var proxy = pricesForKey("MSCI_ACWI", LocalDate.of(2024, 1, 1), 2000.0, 5);
    var ownNav = pricesForKey("EE0000003283", LocalDate.of(2024, 1, 8), 1.05, 5);
    var spliced = new ArrayList<>(proxy);
    spliced.addAll(ownNav);
    var evalDate = ownNav.getLast().date();

    var point = calculator.calculate(spliced, evalDate, evalDate).getFirst();

    assertThat(point.observationCount()).isEqualTo(spliced.size() - 2);
    assertThat((double) point.metrics().get("dailySigma")).isLessThan(0.01);
  }

  @Test
  void aWindowTooShortToStandBehindReportsItsVolatilityWithoutAClass() {
    var prices = pricesFrom(deterministicReturns(500), LocalDate.of(2024, 1, 1));
    var evalDate = prices.getLast().date();

    var point = calculator.calculate(prices, evalDate, evalDate).getFirst();

    assertThat(point.riskClass()).isNull();
    assertThat(point.observationCount()).isEqualTo(499);
    assertThat(point.volatility()).isPositive();
  }

  @Test
  void theVevSpansTheHoldingPeriodRatherThanTheLengthOfTheSample() {
    var longerSample = onlyPointOf(alternatingReturns(1201));
    var shorterSample = onlyPointOf(alternatingReturns(1101));

    assertThat(shorterSample.observationCount()).isNotEqualTo(longerSample.observationCount());
    assertThat(shorterSample.volatility().doubleValue())
        .isCloseTo(longerSample.volatility().doubleValue(), within(1e-6));
  }

  @Test
  void cornishFisherCoefficientsMatchTheirClosedForms() {
    assertThat(SriCalculator.SKEW_COEFFICIENT).isCloseTo((Z * Z - 1) / 6, within(1e-4));
    assertThat(SriCalculator.KURTOSIS_COEFFICIENT)
        .isCloseTo((Z * Z * Z - 3 * Z) / 24, within(1e-4));
    assertThat(SriCalculator.SKEW_SQUARED_COEFFICIENT)
        .isCloseTo((2 * Z * Z * Z - 5 * Z) / 36, within(1e-4));
  }

  @Test
  void ignoresWeekendPrices() {
    var friday = LocalDate.of(2026, 1, 2);
    var prices = new ArrayList<FundValue>();
    var price = 100.0;
    var date = friday.minusYears(5);
    while (!date.isAfter(friday)) {
      price *= 1.0001;
      prices.add(new FundValue(KEY, date, valueOf(price), "MSCI", Instant.EPOCH));
      date = date.plusDays(1);
    }

    var points = calculator.calculate(prices, friday, friday);

    assertThat(points.getFirst().observationCount())
        .isEqualTo(returnsInWindow(prices, friday).size())
        .isLessThan(
            (int) prices.stream().filter(p -> !p.date().isBefore(friday.minusYears(5))).count());
  }

  @Test
  void emitsNoPointWhenWindowHasFewerThanTwoReturns() {
    var prices =
        List.of(
            new FundValue(KEY, LocalDate.of(2026, 1, 1), valueOf(100), "MSCI", Instant.EPOCH),
            new FundValue(KEY, LocalDate.of(2026, 1, 2), valueOf(101), "MSCI", Instant.EPOCH));

    var points = calculator.calculate(prices, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2));

    assertThat(points).isEmpty();
  }

  @Test
  void constantPricesGiveZeroVolatilityAndLowestClassWithoutNaN() {
    var returns = new double[1400];
    var prices = pricesFrom(returns, LocalDate.of(2019, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate);

    var point = points.getFirst();
    assertThat(point.volatility()).isEqualByComparingTo("0");
    assertThat(point.riskClass()).isEqualTo(1);
    assertThat((double) point.metrics().get("skew")).isZero();
    assertThat((double) point.metrics().get("excessKurtosis")).isZero();
  }

  @Test
  void extremeVolatilityStaysInTheCornishFisherDomainAndSaturatesAtTheHighestClass() {
    var returns = deterministicReturns(1400);
    for (int i = 0; i < returns.length; i++) {
      returns[i] = returns[i] * 40;
    }
    var prices = pricesFrom(returns, LocalDate.of(2019, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate);

    assertThat(points.getFirst().riskClass()).isEqualTo(7);
  }

  @Test
  void windowSpansExactlyFiveYears() {
    var returns = deterministicReturns(2200);
    var prices = pricesFrom(returns, LocalDate.of(2016, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate);

    assertThat(points.getFirst().observationCount())
        .isEqualTo(returnsInWindow(prices, evalDate).size());
  }

  private ReferencePoint onlyPointOf(double[] returns) {
    var prices = pricesFrom(returns, LocalDate.of(2021, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate);

    assertThat(points).hasSize(1);
    return points.getFirst();
  }

  private static double[] alternatingReturns(int count) {
    var returns = new double[count];
    for (int i = 0; i < count; i++) {
      returns[i] = i % 2 == 0 ? 0.008 : -0.008;
    }
    return returns;
  }

  private static double[] deterministicReturns(int count) {
    var returns = new double[count];
    for (int i = 0; i < count; i++) {
      returns[i] = 0.004 * Math.sin(i) + 0.002 * Math.cos(i / 3.0) - 0.0003;
    }
    return returns;
  }

  private static List<FundValue> pricesFrom(double[] returns, LocalDate start) {
    var prices = new ArrayList<FundValue>();
    var price = 100.0;
    var date = start;
    for (double logReturn : returns) {
      while (date.getDayOfWeek().getValue() > 5) {
        date = date.plusDays(1);
      }
      price *= Math.exp(logReturn);
      prices.add(new FundValue(KEY, date, valueOf(price), "MSCI", Instant.EPOCH));
      date = date.plusDays(1);
    }
    return prices;
  }

  private static List<FundValue> pricesForKey(
      String key, LocalDate start, double startPrice, int count) {
    var prices = new ArrayList<FundValue>();
    var price = startPrice;
    var date = start;
    for (int i = 0; i < count; i++) {
      while (date.getDayOfWeek().getValue() > 5) {
        date = date.plusDays(1);
      }
      price *= 1.001;
      prices.add(new FundValue(key, date, valueOf(price), "TEST", Instant.EPOCH));
      date = date.plusDays(1);
    }
    return prices;
  }

  private static List<Double> returnsInWindow(List<FundValue> prices, LocalDate evalDate) {
    var weekdays = prices.stream().filter(p -> p.date().getDayOfWeek().getValue() <= 5).toList();
    var windowStart = evalDate.minusYears(5);
    var returns = new ArrayList<Double>();
    for (int i = 1; i < weekdays.size(); i++) {
      var date = weekdays.get(i).date();
      if (date.isAfter(windowStart) && !date.isAfter(evalDate)) {
        returns.add(
            Math.log(
                weekdays.get(i).value().doubleValue() / weekdays.get(i - 1).value().doubleValue()));
      }
    }
    return returns;
  }

  private static double expectedVev(List<Double> returns) {
    int n = returns.size();
    double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double s2 = 0;
    double s3 = 0;
    double s4 = 0;
    for (double r : returns) {
      double d = r - mean;
      s2 += d * d;
      s3 += d * d * d;
      s4 += d * d * d * d;
    }
    double m2 = s2 / n;
    double m3 = s3 / n;
    double m4 = s4 / n;
    double sigma = Math.sqrt(m2);
    double skew = m2 > 0 ? m3 / Math.pow(sigma, 3) : 0;
    double excessKurtosis = m2 > 0 ? m4 / Math.pow(sigma, 4) - 3 : 0;
    int horizon = SriCalculator.HOLDING_PERIOD_TRADING_DAYS;
    double var =
        -sigma
                * Math.sqrt(horizon)
                * (Z
                    - 0.47357647 * (skew / Math.sqrt(horizon))
                    + 0.068717874 * (excessKurtosis / horizon)
                    - 0.146067276 * (skew * skew / horizon))
            - 0.5 * sigma * sigma * horizon;
    return (Math.sqrt(Z * Z - 2 * var) - Z) / Math.sqrt(5);
  }
}
