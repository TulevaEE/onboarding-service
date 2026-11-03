package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarYearReturnsTest {

  private static final String KEY = "EE3600109435";

  @Test
  void eachYearRunsFromThePreviousYearsLastCloseToItsOwnLastClose() {
    var values =
        List.of(
            close("2023-12-29", 100),
            close("2024-06-28", 90),
            close("2024-12-31", 110),
            close("2025-12-31", 99));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2020, 1, 2), 2026);

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2024, new CalendarYearReturn(110.0 / 100 - 1, false),
                2025, new CalendarYearReturn(99.0 / 110 - 1, false)));
  }

  @Test
  void theFundsFirstYearRunsFromItsFirstValueAndIsMarkedPartial() {
    var values = List.of(close("2026-02-01", 1), close("2026-06-30", 1.25));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2026, 2, 1), 2026);

    assertThat(returns).isEqualTo(Map.of(2026, new CalendarYearReturn(0.25, true)));
  }

  @Test
  void aYearAfterAMissingYearIsLeftOutRatherThanChainedAcrossTheGap() {
    var values =
        List.of(
            close("2017-03-28", 1),
            close("2017-12-29", 1.5),
            close("2019-12-31", 2),
            close("2020-12-31", 3));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2017, 3, 28), 2021);

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2017, new CalendarYearReturn(0.5, true),
                2020, new CalendarYearReturn(0.5, false)));
  }

  @Test
  void aYearWhoseClosesStopLongBeforeItsEndIsLeftOutWithTheYearMeasuredFromIt() {
    var values =
        List.of(
            close("2018-12-31", 100),
            close("2019-06-28", 110),
            close("2020-03-02", 90),
            close("2020-12-31", 120),
            close("2021-12-31", 132),
            close("2022-03-31", 140));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2015, 1, 2), 2022);

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2021, new CalendarYearReturn(132.0 / 120 - 1, false),
                2022, new CalendarYearReturn(140.0 / 132 - 1, false)));
  }

  @Test
  void aCloseAWeekBeforeTheThirtyFirstOfDecemberStillEndsTheYear() {
    var values =
        List.of(close("2023-12-24", 100), close("2024-12-24", 110), close("2025-01-31", 121));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2015, 1, 2), 2025);

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2024, new CalendarYearReturn(110.0 / 100 - 1, false),
                2025, new CalendarYearReturn(121.0 / 110 - 1, false)));
  }

  @Test
  void aCloseMoreThanAWeekBeforeTheThirtyFirstOfDecemberDoesNotEndTheYear() {
    var values =
        List.of(close("2023-12-23", 100), close("2024-12-31", 110), close("2025-01-31", 121));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2015, 1, 2), 2025);

    assertThat(returns).isEqualTo(Map.of(2025, new CalendarYearReturn(121.0 / 110 - 1, false)));
  }

  @Test
  void aYearWhoseClosesStopBeforeItsEndIsLeftOutEvenWhenTheyAreTheLastBeforeTheOpenYear() {
    var values =
        List.of(close("2025-12-31", 90), close("2026-12-31", 100), close("2027-12-15", 110));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2015, 1, 2), 2028);

    assertThat(returns).isEqualTo(Map.of(2026, new CalendarYearReturn(100.0 / 90 - 1, false)));
  }

  @Test
  void growthSinceADayRunsFromTheLastCloseOnOrBeforeItToTheYearsLastClose() {
    var values =
        List.of(close("2026-01-30", 100), close("2026-02-02", 101), close("2026-06-30", 110));

    assertThat(CalendarYearReturns.growthSince(values, LocalDate.of(2026, 2, 1), 2026, 2026))
        .hasValue(110.0 / 100 - 1);
  }

  @Test
  void hasNoGrowthSinceADayWhoseLastEarlierCloseIsMoreThanAWeekOld() {
    var values = List.of(close("2026-01-20", 100), close("2026-06-30", 110));

    assertThat(CalendarYearReturns.growthSince(values, LocalDate.of(2026, 2, 1), 2026, 2026))
        .isEmpty();
  }

  @Test
  void hasNoGrowthSinceADayToAYearWhoseClosesStopBeforeItsEndAndBeforeTheOpenYear() {
    var values = List.of(close("2026-01-30", 100), close("2026-12-15", 110));

    assertThat(CalendarYearReturns.growthSince(values, LocalDate.of(2026, 2, 1), 2026, 2027))
        .isEmpty();
  }

  @Test
  void refusesASeriesWithAValueThatIsNotPositive() {
    var values = List.of(close("2024-12-31", 100), close("2025-06-30", 0), close("2025-12-31", 99));

    assertThatThrownBy(() -> CalendarYearReturns.of(values, LocalDate.of(2020, 1, 2), 2026))
        .isInstanceOf(PastPerformanceRefusedException.class);
  }

  private static FundValue close(String date, double value) {
    return aFundValue(KEY, LocalDate.parse(date), value);
  }
}
