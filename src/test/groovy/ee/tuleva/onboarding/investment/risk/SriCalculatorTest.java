package ee.tuleva.onboarding.investment.risk;

import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
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

    var points = calculator.calculate(prices, evalDate, evalDate).points();

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

    var points = calculator.calculate(prices, firstReturnDate, prices.getLast().date()).points();

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

    var point = calculator.calculate(spliced, evalDate, evalDate).points().getFirst();

    assertThat(point.observationCount()).isEqualTo(spliced.size() - 2);
    assertThat((double) point.metrics().get("dailySigma")).isLessThan(0.01);
  }

  @Test
  void theHoldingPeriodSpansTheSupervisoryTradingYear() {
    assertThat(SriCalculator.HOLDING_PERIOD_TRADING_DAYS).isEqualTo(5 * 256);
  }

  @Test
  void aWindowTooShortToStandBehindReportsItsVolatilityWithoutAClass() {
    var prices = pricesFrom(deterministicReturns(500), LocalDate.of(2024, 1, 1));
    var evalDate = prices.getLast().date();

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    assertThat(point.riskClass()).isNull();
    assertThat(point.observationCount()).isEqualTo(499);
    assertThat(point.volatility()).isPositive();
  }

  @Test
  void aWindowAtTheAnnexTwoMinimumOfTwoYearsGetsAClass() {
    var prices = pricesFrom(deterministicReturns(600), LocalDate.of(2024, 1, 1));
    var evalDate = prices.getLast().date();

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    assertThat(point.observationCount()).isEqualTo(599).isGreaterThanOrEqualTo(2 * 256);
    assertThat(point.riskClass()).isNotNull();
  }

  @Test
  void publishesAtExactlyTheAnnexTwoMinimumObservationCount() {
    var prices = new ArrayList<FundValue>();
    var monday = LocalDate.of(2020, 1, 6);
    for (int week = 0; week <= 200; week++) {
      prices.add(new FundValue(KEY, monday.plusWeeks(week), valueOf(100), "MSCI", Instant.EPOCH));
      if (week < 200) {
        prices.add(
            new FundValue(
                KEY, monday.plusWeeks(week).plusDays(3), valueOf(100), "MSCI", Instant.EPOCH));
      }
    }
    var evalDate = monday.plusWeeks(200);

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    assertThat(point.observationCount()).isEqualTo(400);
    assertThat(point.riskClass()).isNotNull();
  }

  @Test
  void twoCalendarYearsOfPricesGetAClassEvenWhenHolidaysThinTheDayCount() {
    var evalDate = LocalDate.of(2026, 1, 2);
    var prices = weekdayPricesWithHolidays(evalDate.minusYears(2).minusWeeks(1), evalDate);

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    assertThat(point.observationCount()).isLessThan(2 * 256);
    assertThat(point.riskClass()).isNotNull();
  }

  @Test
  void theTwoYearsRunFromThePriceTheEarliestReturnWasTakenAgainst() {
    var evalDate = LocalDate.of(2026, 1, 2);
    var exactlyTwoYears = weekdayPricesWithHolidays(evalDate.minusYears(2), evalDate);
    var oneDayShort = weekdayPricesWithHolidays(evalDate.minusYears(2).plusDays(1), evalDate);

    var granted = calculator.calculate(exactlyTwoYears, evalDate, evalDate).points().getFirst();
    var withheld = calculator.calculate(oneDayShort, evalDate, evalDate).points().getFirst();

    assertThat(granted.riskClass()).isNotNull();
    assertThat(withheld.riskClass()).isNull();
    assertThat(withheld.observationCount())
        .isGreaterThan(400)
        .isEqualTo(granted.observationCount() - 1);
  }

  @Test
  void aPointThatCannotBeEvaluatedDoesNotTakeTheRestOfTheSeriesDownWithIt() {
    var prices = new ArrayList<>(pricesFrom(deterministicReturns(1400), LocalDate.of(2019, 1, 1)));
    var corrupted = prices.get(1300);
    prices.set(
        1300,
        new FundValue(KEY, corrupted.date(), new BigDecimal("1E+400"), "MSCI", Instant.EPOCH));
    var healthyDate = prices.get(1200).date();

    var points =
        calculator.calculate(prices, prices.getFirst().date(), prices.getLast().date()).points();

    assertThat(points.stream().map(ReferencePoint::date)).contains(healthyDate);
    assertThat(points.stream().map(ReferencePoint::date))
        .doesNotContain(prices.get(1300).date(), prices.getLast().date());
  }

  @Test
  void theDatesItCouldNotEvaluateLeaveWithTheSeriesRatherThanOnlyWithTheLog() {
    var prices = new ArrayList<>(pricesFrom(deterministicReturns(1400), LocalDate.of(2019, 1, 1)));
    var corrupted = prices.get(1300);
    prices.set(
        1300,
        new FundValue(KEY, corrupted.date(), new BigDecimal("1E+400"), "MSCI", Instant.EPOCH));

    var series = calculator.calculate(prices, prices.getFirst().date(), prices.getLast().date());

    assertThat(series.skippedDates())
        .contains(prices.get(1300).date(), prices.getLast().date())
        .doesNotContain(prices.get(1200).date())
        .doesNotContainAnyElementsOf(series.points().stream().map(ReferencePoint::date).toList());
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

    var points = calculator.calculate(prices, friday, friday).points();

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

    var series = calculator.calculate(prices, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2));

    assertThat(series.points()).isEmpty();
    assertThat(series.skippedDates()).isEmpty();
  }

  @Test
  void excludesNonPositivePrices() {
    var prices =
        List.of(
            new FundValue(KEY, LocalDate.of(2026, 1, 1), valueOf(100), "MSCI", Instant.EPOCH),
            new FundValue(KEY, LocalDate.of(2026, 1, 2), BigDecimal.ZERO, "MSCI", Instant.EPOCH),
            new FundValue(KEY, LocalDate.of(2026, 1, 5), valueOf(101), "MSCI", Instant.EPOCH));

    var series = calculator.calculate(prices, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5));

    assertThat(series.points()).isEmpty();
    assertThat(series.skippedDates()).isEmpty();
  }

  @Test
  void valueAtRiskSubtractsHalfVarianceTimesHorizonFromTheCornishFisherTerm() {
    var returns = new double[] {0.0, 0.008, -0.008, 0.008, -0.008};
    var prices = pricesFrom(returns, LocalDate.of(2024, 1, 1));
    var evalDate = prices.getLast().date();

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    assertThat((double) point.metrics().get("valueAtRisk"))
        .isCloseTo(-0.6019036943770, within(1e-6));
  }

  @Test
  void valueAtRiskSubtractsTheSkewSquaredTerm() {
    var returns = new double[] {0.0, 0.006, 0.006, -0.012};
    var prices = pricesFrom(returns, LocalDate.of(2024, 1, 1));
    var evalDate = prices.getLast().date();

    var point = calculator.calculate(prices, evalDate, evalDate).points().getFirst();

    var skew = -1 / Math.sqrt(2);
    var excessKurtosis = -1.5;
    var sigma = 0.006 * Math.sqrt(2);
    var horizon = SriCalculator.HOLDING_PERIOD_TRADING_DAYS;
    var rootHorizon = Math.sqrt(horizon);
    var expected =
        -sigma
                * rootHorizon
                * (1.95996398454005
                    - 0.47357647 * (skew / rootHorizon)
                    + 0.068717874 * (excessKurtosis / horizon)
                    - 0.146067276 * (skew * skew / horizon))
            - 0.5 * sigma * sigma * horizon;

    assertThat((double) point.metrics().get("valueAtRisk")).isCloseTo(expected, within(1e-7));
  }

  @Test
  void constantPricesGiveZeroVolatilityAndLowestClassWithoutNaN() {
    var returns = new double[1400];
    var prices = pricesFrom(returns, LocalDate.of(2019, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate).points();

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

    var points = calculator.calculate(prices, evalDate, evalDate).points();

    assertThat(points.getFirst().riskClass()).isEqualTo(7);
  }

  @Test
  void windowSpansExactlyFiveYears() {
    var returns = deterministicReturns(2200);
    var prices = pricesFrom(returns, LocalDate.of(2016, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate).points();

    assertThat(points.getFirst().observationCount())
        .isEqualTo(returnsInWindow(prices, evalDate).size());
  }

  private ReferencePoint onlyPointOf(double[] returns) {
    var prices = pricesFrom(returns, LocalDate.of(2021, 1, 1));
    var evalDate = prices.getLast().date();

    var points = calculator.calculate(prices, evalDate, evalDate).points();

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

  private static List<FundValue> weekdayPricesWithHolidays(LocalDate start, LocalDate end) {
    var prices = new ArrayList<FundValue>();
    var price = 100.0;
    var date = start;
    var weekday = 0;
    while (!date.isAfter(end)) {
      if (date.getDayOfWeek().getValue() <= 5 && ++weekday % 25 != 0) {
        price *= Math.exp(0.004 * Math.sin(weekday) - 0.0003);
        prices.add(new FundValue(KEY, date, valueOf(price), "MSCI", Instant.EPOCH));
      }
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
