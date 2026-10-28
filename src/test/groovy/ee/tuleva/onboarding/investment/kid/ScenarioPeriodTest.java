package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ScenarioPeriodTest {

  private static final String KEY = "MSCI_ACWI";
  private static final ScenarioPeriod TEN_YEARS =
      new ScenarioPeriod(LocalDate.of(2016, 9, 30), LocalDate.of(2026, 9, 30));

  @Test
  void endsWithTheLastCompleteMonthAndReachesBackTenYears() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22));

    assertThat(period)
        .isEqualTo(new ScenarioPeriod(LocalDate.of(2016, 9, 30), LocalDate.of(2026, 9, 30)));
  }

  @Test
  void endsWithTheMonthBeforeWhileTheSeriesLagsAFewDaysIntoANewMonth() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2));

    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  void acceptsALastPriceExactlyTenDaysOld() {
    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 22));

    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  void refusesASeriesWhoseLastPriceIsMoreThanTenDaysOldSoAStalledFeedGivesNoFigures() {
    assertThatThrownBy(
            () ->
                ScenarioPeriod.endingWithLastCompleteMonth(
                    LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 22)))
        .isInstanceOf(KidCalculationRefusedException.class);
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

    assertThat(period.riskHistory(prices))
        .containsExactly(
            close("2025-01-31", 100), close("2025-02-03", 102), close("2025-02-07", 103));
  }

  @Test
  void acceptsAWeekWithoutClosesAsAMarketClosure() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 7));
    var prices = List.of(close("2025-01-31", 100), close("2025-02-07", 101));

    assertThat(period.riskHistory(prices)).isEqualTo(prices);
  }

  @Test
  void refusesDailyClosesWithAGapLongerThanAWeekSoMonthlyDataIsNotTakenForDaily() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28));
    var prices = List.of(close("2025-01-31", 100), close("2025-02-10", 101));

    assertThatThrownBy(() -> period.riskHistory(prices))
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

    assertThatThrownBy(() -> period.riskHistory(wednesdays))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesDailyClosesThatSkipTwoWeekdaysEveryWeek() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31));
    var mondaysWednesdaysAndFridays =
        Stream.iterate(
                LocalDate.of(2025, 1, 31),
                day -> !day.isAfter(LocalDate.of(2025, 3, 31)),
                day -> day.plusDays(1))
            .filter(day -> Set.of(MONDAY, WEDNESDAY, FRIDAY).contains(day.getDayOfWeek()))
            .map(day -> close(day.toString(), 100))
            .toList();

    assertThatThrownBy(() -> period.riskHistory(mondaysWednesdaysAndFridays))
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
            .filter(day -> !Set.of(SATURDAY, SUNDAY).contains(day.getDayOfWeek()))
            .filter(day -> !holidays.contains(day))
            .map(day -> close(day.toString(), 100))
            .toList();

    assertThat(period.riskHistory(closesOutsideTheHolidays))
        .isEqualTo(
            closesOutsideTheHolidays.stream()
                .filter(close -> close.date().isAfter(LocalDate.of(2025, 11, 30)))
                .toList());
  }

  @Test
  void acceptsAMonthWithClosesOnExactlySeventyPercentOfItsWeekdays() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28));
    var fourteenOfTwentyWeekdays =
        februaryWeekdaysExcept(
            Set.of(
                LocalDate.of(2025, 2, 5),
                LocalDate.of(2025, 2, 7),
                LocalDate.of(2025, 2, 12),
                LocalDate.of(2025, 2, 14),
                LocalDate.of(2025, 2, 19),
                LocalDate.of(2025, 2, 26)));

    assertThat(period.riskHistory(fourteenOfTwentyWeekdays)).isEqualTo(fourteenOfTwentyWeekdays);
  }

  @Test
  void refusesAMonthWithClosesOnFewerThanSeventyPercentOfItsWeekdays() {
    var period = new ScenarioPeriod(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28));
    var thirteenOfTwentyWeekdays =
        februaryWeekdaysExcept(
            Set.of(
                LocalDate.of(2025, 2, 5),
                LocalDate.of(2025, 2, 7),
                LocalDate.of(2025, 2, 12),
                LocalDate.of(2025, 2, 14),
                LocalDate.of(2025, 2, 19),
                LocalDate.of(2025, 2, 21),
                LocalDate.of(2025, 2, 26)));

    assertThatThrownBy(() -> period.riskHistory(thirteenOfTwentyWeekdays))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void theRiskHistoryRunsFromTheLastCloseOnOrBeforeFiveYearsBeforeThePeriodEnd() {
    var prices = SyntheticIndexSeries.weekdayCloses(KEY);

    assertThat(TEN_YEARS.riskHistory(prices))
        .isEqualTo(closesBetween(prices, "2021-09-30", "2026-09-30"));
  }

  @Test
  void aFiveYearBoundaryOnASundayStartsTheRiskHistoryWithTheFridayCloseBeforeIt() {
    var periodEndingOnASaturday =
        new ScenarioPeriod(LocalDate.of(2016, 1, 31), LocalDate.of(2026, 1, 31));
    var prices = SyntheticIndexSeries.weekdayCloses(KEY);

    assertThat(periodEndingOnASaturday.riskHistory(prices))
        .isEqualTo(closesBetween(prices, "2021-01-29", "2026-01-31"));
  }

  @Test
  void acceptsAGapLongerThanAWeekBeforeTheRiskHistory() {
    var prices = syntheticWithout(between("2018-03-05", "2018-03-12"));

    assertThat(TEN_YEARS.riskHistory(prices))
        .isEqualTo(closesBetween(prices, "2021-09-30", "2026-09-30"));
  }

  @Test
  void refusesAGapLongerThanAWeekInsideTheRiskHistory() {
    var prices = syntheticWithout(between("2023-03-06", "2023-03-13"));

    assertThatThrownBy(() -> TEN_YEARS.riskHistory(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void aGapEndingOnTheFirstCloseOfTheRiskHistoryComesBeforeTheFirstReturn() {
    var prices = syntheticWithout(between("2021-09-23", "2021-09-29"));

    assertThat(TEN_YEARS.riskHistory(prices))
        .isEqualTo(closesBetween(prices, "2021-09-30", "2026-09-30"));
  }

  @Test
  void refusesAGapStartingOnTheFirstCloseOfTheRiskHistoryBecauseTheFirstReturnSpansIt() {
    var prices = syntheticWithout(between("2021-10-01", "2021-10-07"));

    assertThatThrownBy(() -> TEN_YEARS.riskHistory(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void acceptsTooFewClosesInTheMonthTheRiskHistoryStartsAtTheEndOf() {
    var prices = syntheticWithout(tuesdaysAndThursdaysFromUntil("2021-09-01", "2021-09-30"));

    assertThat(TEN_YEARS.riskHistory(prices))
        .isEqualTo(closesBetween(prices, "2021-09-30", "2026-09-30"));
  }

  @Test
  void refusesTooFewClosesInTheFirstWholeMonthOfTheRiskHistory() {
    var prices = syntheticWithout(tuesdaysAndThursdaysFromUntil("2021-10-01", "2021-11-01"));

    assertThatThrownBy(() -> TEN_YEARS.riskHistory(prices))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  private static List<FundValue> syntheticWithout(Predicate<LocalDate> skipped) {
    return SyntheticIndexSeries.weekdayCloses(KEY).stream()
        .filter(close -> !skipped.test(close.date()))
        .toList();
  }

  private static Predicate<LocalDate> between(String first, String last) {
    return day -> !day.isBefore(LocalDate.parse(first)) && !day.isAfter(LocalDate.parse(last));
  }

  private static Predicate<LocalDate> tuesdaysAndThursdaysFromUntil(String from, String until) {
    return day ->
        !day.isBefore(LocalDate.parse(from))
            && day.isBefore(LocalDate.parse(until))
            && Set.of(TUESDAY, THURSDAY).contains(day.getDayOfWeek());
  }

  private static List<FundValue> closesBetween(List<FundValue> prices, String first, String last) {
    return prices.stream()
        .filter(close -> !close.date().isBefore(LocalDate.parse(first)))
        .filter(close -> !close.date().isAfter(LocalDate.parse(last)))
        .toList();
  }

  private static List<FundValue> februaryWeekdaysExcept(Set<LocalDate> skipped) {
    return Stream.iterate(
            LocalDate.of(2025, 1, 31),
            day -> !day.isAfter(LocalDate.of(2025, 2, 28)),
            day -> day.plusDays(1))
        .filter(day -> !Set.of(SATURDAY, SUNDAY).contains(day.getDayOfWeek()))
        .filter(day -> !skipped.contains(day))
        .map(day -> close(day.toString(), 100))
        .toList();
  }

  private static FundValue close(String date, double value) {
    return aFundValue(KEY, LocalDate.parse(date), value);
  }
}
