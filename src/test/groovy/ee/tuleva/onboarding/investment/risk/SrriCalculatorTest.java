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

class SrriCalculatorTest {

  private static final String KEY = "EE3600109435";
  private static final LocalDate FIRST_MONDAY = LocalDate.of(2020, 1, 6);

  private final SrriCalculator calculator = new SrriCalculator();

  @Test
  void annualisesTheSampleStandardDeviationOfWeeklyReturns() {
    var navs = weeklyNavs(FIRST_MONDAY, List.of(100.0, 110.0, 99.0, 108.9, 108.9));
    var evalDate = weekEnd(FIRST_MONDAY.plusWeeks(3));

    var points = calculator.calculate(navs, evalDate, evalDate);

    var point = points.getFirst();
    assertThat(point.observationCount()).isEqualTo(3);
    assertThat(point.volatility().doubleValue()).isCloseTo(0.8326670, within(1e-6));
  }

  @Test
  void anEmptyPriceSeriesProducesNoReferencePoints() {
    assertThat(calculator.calculate(List.of(), FIRST_MONDAY, FIRST_MONDAY.plusWeeks(4))).isEmpty();
  }

  @Test
  void aSingleIncompleteWeekProducesNoReferencePoints() {
    var navs = List.of(nav(FIRST_MONDAY, 100.0), nav(FIRST_MONDAY.plusDays(2), 105.0));

    assertThat(calculator.calculate(navs, FIRST_MONDAY, FIRST_MONDAY.plusWeeks(4))).isEmpty();
  }

  @Test
  void doesNotComputeAReturnAcrossAMissingWeek() {
    var navs = new ArrayList<FundValue>();
    for (int week : List.of(0, 1, 2, 4, 5, 6)) {
      navs.add(nav(weekEnd(FIRST_MONDAY.plusWeeks(week)), 100.0 + week));
    }
    var evalDate = weekEnd(FIRST_MONDAY.plusWeeks(5));

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.observationCount()).isEqualTo(3);
  }

  @Test
  void noReturnIsComputedBetweenTwoDifferentSources() {
    var navs = new ArrayList<FundValue>();
    for (int week = 0; week < 4; week++) {
      navs.add(navForKey("MSCI_ACWI", weekEnd(FIRST_MONDAY.plusWeeks(week)), 2000.0 + week));
    }
    for (int week = 4; week < 8; week++) {
      navs.add(
          navForKey("EE0000003283", weekEnd(FIRST_MONDAY.plusWeeks(week)), 1.05 + week * 0.01));
    }
    var evalDate = weekEnd(FIRST_MONDAY.plusWeeks(6));

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.observationCount()).isEqualTo(5);
    assertThat(point.volatility().doubleValue()).isLessThan(0.5);
  }

  @Test
  void usesTheLastNavOfEachWeek() {
    var navs =
        List.of(
            nav(FIRST_MONDAY, 100.0),
            nav(FIRST_MONDAY.plusDays(2), 105.0),
            nav(FIRST_MONDAY.plusWeeks(1), 200.0),
            nav(FIRST_MONDAY.plusWeeks(1).plusDays(3), 210.0),
            nav(FIRST_MONDAY.plusWeeks(2), 220.0),
            nav(FIRST_MONDAY.plusWeeks(2).plusDays(2), 231.0),
            nav(FIRST_MONDAY.plusWeeks(3), 999.0));
    var evalDate = FIRST_MONDAY.plusWeeks(2).plusDays(2);

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.observationCount()).isEqualTo(2);
    assertThat(point.volatility().doubleValue()).isCloseTo(4.5891, within(1e-3));
  }

  @Test
  void excludesTheCurrentIncompleteWeek() {
    var navs = weeklyNavs(FIRST_MONDAY, List.of(100.0, 110.0, 99.0, 108.9));
    navs.add(nav(FIRST_MONDAY.plusWeeks(4).plusDays(1), 500.0));

    var points = calculator.calculate(navs, FIRST_MONDAY, FIRST_MONDAY.plusWeeks(5));

    assertThat(points.stream().map(ReferencePoint::date))
        .doesNotContain(FIRST_MONDAY.plusWeeks(4).plusDays(1));
  }

  @Test
  void publishesNoClassButKeepsVolatilityWhenHistoryIsTooShort() {
    var navs = weeklyNavs(FIRST_MONDAY, List.of(100.0, 110.0, 99.0, 108.9, 108.9));
    var evalDate = weekEnd(FIRST_MONDAY.plusWeeks(3));

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.riskClass()).isNull();
    assertThat(point.volatility()).isPositive();
  }

  @Test
  void publishesAClassOnceTheFiveYearWindowHasEnoughObservations() {
    var navs = fiveYearsOfNavs(300);
    var evalDate = navs.getLast().date().minusWeeks(1);

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.riskClass()).isNotNull();
    assertThat(point.observationCount()).isGreaterThanOrEqualTo(200);
  }

  @Test
  void publishesAClassOnObservationCountAloneEvenWhenTheWindowReachesPastTheFirstNav() {
    var navs = fiveYearsOfNavs(250);
    var evalDate = navs.getLast().date().minusWeeks(1);

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.observationCount()).isGreaterThanOrEqualTo(200);
    assertThat((LocalDate) point.metrics().get("windowStart")).isBefore(navs.getFirst().date());
    assertThat(point.riskClass()).isNotNull();
  }

  @Test
  void oneMissingWeekDoesNotBlankTheClassAndTheWindowStaysFiveCalendarYears() {
    var navs = fiveYearsOfNavs(300);
    var dropped = navs.get(150).date();
    navs.removeIf(value -> value.date().equals(dropped));
    var evalDate = navs.getLast().date().minusWeeks(1);

    var point = calculator.calculate(navs, evalDate, evalDate).getFirst();

    assertThat(point.riskClass()).isNotNull();
    assertThat(point.observationCount())
        .isEqualTo(
            calculator
                    .calculate(fiveYearsOfNavs(300), evalDate, evalDate)
                    .getFirst()
                    .observationCount()
                - 2);
    assertThat((LocalDate) point.metrics().get("windowStart")).isEqualTo(evalDate.minusYears(5));
  }

  private static List<FundValue> weeklyNavs(LocalDate firstMonday, List<Double> values) {
    var navs = new ArrayList<FundValue>();
    for (int i = 0; i < values.size(); i++) {
      navs.add(nav(weekEnd(firstMonday.plusWeeks(i)), values.get(i)));
    }
    return navs;
  }

  private static ArrayList<FundValue> fiveYearsOfNavs(int weeks) {
    var navs = new ArrayList<FundValue>();
    var value = 1.0;
    for (int i = 0; i < weeks; i++) {
      value *= 1 + 0.01 * Math.sin(i);
      navs.add(nav(weekEnd(FIRST_MONDAY.plusWeeks(i)), value));
    }
    return navs;
  }

  private static LocalDate weekEnd(LocalDate monday) {
    return monday.plusDays(4);
  }

  private static FundValue nav(LocalDate date, double value) {
    return navForKey(KEY, date, value);
  }

  private static FundValue navForKey(String key, LocalDate date, double value) {
    return new FundValue(key, date, valueOf(value), "PENSIONIKESKUS", Instant.EPOCH);
  }
}
