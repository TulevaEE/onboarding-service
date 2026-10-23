package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;

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

    var returns = CalendarYearReturns.of(values, LocalDate.of(2020, 1, 2));

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2024, new CalendarYearReturn(110.0 / 100 - 1, false),
                2025, new CalendarYearReturn(99.0 / 110 - 1, false)));
  }

  @Test
  void theFundsFirstYearRunsFromItsFirstValueAndIsMarkedPartial() {
    var values = List.of(close("2026-02-01", 1), close("2026-06-30", 1.25));

    var returns = CalendarYearReturns.of(values, LocalDate.of(2026, 2, 1));

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

    var returns = CalendarYearReturns.of(values, LocalDate.of(2017, 3, 28));

    assertThat(returns)
        .isEqualTo(
            Map.of(
                2017, new CalendarYearReturn(0.5, true),
                2020, new CalendarYearReturn(0.5, false)));
  }

  private static FundValue close(String date, double value) {
    return aFundValue(KEY, LocalDate.parse(date), value);
  }
}
