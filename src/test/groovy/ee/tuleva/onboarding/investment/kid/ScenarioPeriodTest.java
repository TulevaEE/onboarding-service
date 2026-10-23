package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ScenarioPeriodTest {

  private static final String KEY = "MSCI_ACWI";

  @Test
  void endsWithTheLastCompleteMonthAndReachesBackTenYears() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22));

    assertThat(period)
        .isEqualTo(new ScenarioPeriod(LocalDate.of(2016, 9, 30), LocalDate.of(2026, 9, 30)));
  }

  @Test
  void endsWithTheLastMonthThePricesReachWhenTheSeriesStoppedEarlier() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 8, 14), LocalDate.of(2026, 10, 22));

    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  void theLastMonthCountsOnlyOnceTheSeriesReachesItsLastWeekday() {
    var today = LocalDate.of(2026, 10, 1);

    assertThat(ScenarioPeriod.endingWithLastCompleteMonth(LocalDate.of(2026, 9, 29), today).end())
        .isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(ScenarioPeriod.endingWithLastCompleteMonth(LocalDate.of(2026, 9, 30), today).end())
        .isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  void aMonthEndingOnAWeekendIsReachedByItsLastFriday() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 1, 30), LocalDate.of(2026, 2, 2));

    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 1, 31));
  }

  @Test
  void aPeriodEndingInALeapFebruaryStartsOnTheLastDayOfFebruaryTenYearsEarlier() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2028, 3, 10), LocalDate.of(2028, 3, 11));

    assertThat(period)
        .isEqualTo(new ScenarioPeriod(LocalDate.of(2018, 2, 28), LocalDate.of(2028, 2, 29)));
  }

  @Test
  void takesTheLastCloseOnOrBeforeEachMonthEndWithoutDriftingAfterFebruary() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 5, 31));
    var prices =
        List.of(
            close("2025-01-31", 100),
            close("2025-02-27", 101),
            close("2025-02-28", 102),
            close("2025-03-31", 103),
            close("2025-04-30", 104),
            close("2025-05-30", 105),
            close("2025-06-02", 106));

    var monthEnds = period.monthEndPrices(prices);

    assertThat(monthEnds)
        .containsExactly(
            new MonthEndPrice(LocalDate.of(2025, 1, 31), 100),
            new MonthEndPrice(LocalDate.of(2025, 2, 28), 102),
            new MonthEndPrice(LocalDate.of(2025, 3, 31), 103),
            new MonthEndPrice(LocalDate.of(2025, 4, 30), 104),
            new MonthEndPrice(LocalDate.of(2025, 5, 31), 105));
  }

  @Test
  void refusesAMonthEndWhoseLastCloseIsMoreThanAWeekOld() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31));
    var prices =
        List.of(close("2025-01-31", 100), close("2025-02-28", 101), close("2025-03-21", 102));

    assertThatThrownBy(() -> period.monthEndPrices(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void acceptsAMonthEndWhoseLastCloseIsExactlyAWeekOld() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 31));
    var prices = List.of(close("2025-02-28", 100), close("2025-03-24", 101));

    assertThat(period.monthEndPrices(prices))
        .containsExactly(
            new MonthEndPrice(LocalDate.of(2025, 2, 28), 100),
            new MonthEndPrice(LocalDate.of(2025, 3, 31), 101));
  }

  @Test
  void refusesAPeriodTheSeriesDoesNotReachBackTo() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28));
    var prices = List.of(close("2025-02-28", 101));

    assertThatThrownBy(() -> period.monthEndPrices(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void loadsFromAWeekBeforeTheStartSoTheFirstMonthEndHasAClose() {
    var period = new ScenarioPeriod(LocalDate.of(2016, 9, 30), LocalDate.of(2026, 9, 30));

    assertThat(period.loadFrom()).isEqualTo(LocalDate.of(2016, 9, 23));
  }

  @Test
  void dailyClosesAreTheWeekdaysInsideThePeriodInDateOrder() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 7));
    var prices =
        List.of(
            close("2025-02-03", 102),
            close("2025-01-30", 99),
            close("2025-01-31", 100),
            close("2025-02-01", 101),
            close("2025-02-07", 103),
            close("2025-02-10", 104));

    assertThat(period.dailyCloses(prices))
        .containsExactly(
            close("2025-01-31", 100), close("2025-02-03", 102), close("2025-02-07", 103));
  }

  @Test
  void acceptsAWeekWithoutClosesAsAMarketClosure() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 7));
    var prices = List.of(close("2025-01-31", 100), close("2025-02-07", 101));

    assertThat(period.dailyCloses(prices)).hasSize(2);
  }

  @Test
  void refusesDailyClosesWithAGapLongerThanAWeekSoMonthlyDataIsNotTakenForDaily() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28));
    var prices = List.of(close("2025-01-31", 100), close("2025-02-10", 101));

    assertThatThrownBy(() -> period.dailyCloses(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesDailyClosesThatComeOnceAWeekSoWeeklyDataIsNotTakenForDaily() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31));
    var wednesdays =
        Stream.iterate(
                LocalDate.of(2025, 1, 29),
                day -> !day.isAfter(LocalDate.of(2025, 3, 31)),
                day -> day.plusWeeks(1))
            .map(day -> close(day.toString(), 100))
            .toList();

    assertThatThrownBy(() -> period.dailyCloses(wednesdays))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void acceptsAMonthWhoseHolidaysHaveNoClose() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 11, 30), LocalDate.of(2025, 12, 31));
    var holidays =
        Set.of(
            LocalDate.of(2025, 12, 24),
            LocalDate.of(2025, 12, 25),
            LocalDate.of(2025, 12, 26),
            LocalDate.of(2025, 12, 31));
    var closesOutsideTheHolidays =
        Stream.iterate(
                LocalDate.of(2025, 11, 28),
                day -> !day.isAfter(LocalDate.of(2025, 12, 31)),
                day -> day.plusDays(1))
            .filter(day -> !holidays.contains(day))
            .map(day -> close(day.toString(), 100))
            .toList();

    assertThat(period.dailyCloses(closesOutsideTheHolidays)).hasSize(19);
  }

  private static FundValue close(String date, double value) {
    return aFundValue(KEY, LocalDate.parse(date), value);
  }
}
