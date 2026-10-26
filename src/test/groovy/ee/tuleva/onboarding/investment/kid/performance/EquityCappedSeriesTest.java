package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquityCappedSeriesTest {

  private static final EquityCap CAP =
      new EquityCap("BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2));
  private static final LocalDate FUND_STARTED_LONG_BEFORE = LocalDate.of(2015, 1, 2);

  @Test
  void aYearUnderTheCapHoldsTheEquityWeightFromThePreviousYearEndNotFromMidYear() {
    var series =
        EquityCappedSeries.of(
            List.of(
                equity("2016-12-30", 100),
                equity("2017-12-29", 120),
                equity("2018-06-29", 130),
                equity("2018-12-31", 108)),
            List.of(
                bond("2016-12-30", 50),
                bond("2017-12-29", 50),
                bond("2018-06-29", 49),
                bond("2018-12-31", 55)),
            CAP,
            FUND_STARTED_LONG_BEFORE);

    assertThat(growth(series, "2017-12-29", "2018-12-31"))
        .isCloseTo(0.75 * 108 / 120 + 0.25 * 55 / 50, within(1e-12));
  }

  @Test
  void theYearTheCapWasLiftedHoldsTheEquityWeightUntilTheLiftAndOnlyEquityAfter() {
    var series =
        EquityCappedSeries.of(
            List.of(
                equity("2018-12-31", 100),
                equity("2019-09-02", 110),
                equity("2019-09-03", 99),
                equity("2019-12-31", 121)),
            List.of(bond("2018-12-31", 50), bond("2019-09-02", 52), bond("2019-09-03", 60)),
            CAP,
            FUND_STARTED_LONG_BEFORE);

    assertThat(growth(series, "2018-12-31", "2019-12-31"))
        .isCloseTo((0.75 * 110 / 100 + 0.25 * 52 / 50) * 121 / 110, within(1e-12));
  }

  @Test
  void theFundsFirstDayStartsTheEquityWeightAfresh() {
    var series =
        EquityCappedSeries.of(
            List.of(
                equity("2016-12-30", 100), equity("2017-03-28", 105), equity("2017-12-29", 126)),
            List.of(bond("2016-12-30", 50), bond("2017-03-28", 50), bond("2017-12-29", 52)),
            CAP,
            LocalDate.of(2017, 3, 28));

    assertThat(growth(series, "2017-03-28", "2017-12-29"))
        .isCloseTo(0.75 * 126 / 105 + 0.25 * 52 / 50, within(1e-12));
  }

  @Test
  void aYearAfterTheCapWasLiftedIsEquityOnly() {
    var series =
        EquityCappedSeries.of(
            List.of(equity("2019-12-31", 100), equity("2020-12-31", 90)),
            List.of(bond("2019-09-09", 53)),
            CAP,
            FUND_STARTED_LONG_BEFORE);

    assertThat(growth(series, "2019-12-31", "2020-12-31")).isCloseTo(0.9, within(1e-12));
  }

  @Test
  void leavesOutADayUnderTheCapWithoutABondCloseInTheWeekBefore() {
    var series =
        EquityCappedSeries.of(
            List.of(
                equity("2016-05-31", 100), equity("2016-06-01", 101), equity("2016-06-02", 102)),
            List.of(bond("2016-06-01", 50), bond("2016-06-02", 51)),
            CAP,
            FUND_STARTED_LONG_BEFORE);

    assertThat(series)
        .extracting(FundValue::date)
        .containsExactly(LocalDate.of(2016, 6, 1), LocalDate.of(2016, 6, 2));
  }

  @Test
  void refusesWhenTheBondStopsMoreThanAWeekBeforeTheLastCloseUnderTheCap() {
    var equity = List.of(equity("2019-08-01", 100), equity("2019-09-02", 101));
    var bond = List.of(bond("2019-08-01", 50));

    assertThatThrownBy(() -> EquityCappedSeries.of(equity, bond, CAP, FUND_STARTED_LONG_BEFORE))
        .isInstanceOf(PastPerformanceRefusedException.class);
  }

  @Test
  void refusesABondCloseThatIsNotPositive() {
    var equity = List.of(equity("2018-12-31", 100), equity("2019-01-02", 101));
    var bond = List.of(bond("2018-12-31", 50), bond("2019-01-02", 0));

    assertThatThrownBy(() -> EquityCappedSeries.of(equity, bond, CAP, FUND_STARTED_LONG_BEFORE))
        .isInstanceOf(PastPerformanceRefusedException.class);
  }

  private static double growth(List<FundValue> series, String from, String to) {
    return valueOn(series, to) / valueOn(series, from);
  }

  private static double valueOn(List<FundValue> series, String date) {
    return series.stream()
        .filter(value -> value.date().equals(LocalDate.parse(date)))
        .findFirst()
        .orElseThrow()
        .value()
        .doubleValue();
  }

  private static FundValue equity(String date, double value) {
    return aFundValue("MSCI_ACWI", LocalDate.parse(date), value);
  }

  private static FundValue bond(String date, double value) {
    return aFundValue("BOND", LocalDate.parse(date), value);
  }
}
