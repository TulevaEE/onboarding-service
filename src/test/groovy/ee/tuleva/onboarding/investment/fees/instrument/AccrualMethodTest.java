package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.AccrualMethod.DAILY_HOLDING;
import static ee.tuleva.onboarding.investment.fees.instrument.AccrualMethod.MONTH_END_HOLDING;
import static ee.tuleva.onboarding.investment.fees.instrument.AccrualMethod.QUARTER_AVERAGE_MONTH_END;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsBasis.SETTLED;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsBasis.TRADE_DATE;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AccrualMethodTest {

  private static final BigDecimal HOLDING_VALUE = new BigDecimal("1000000.00");
  private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.0005");

  @Test
  void theDailyHoldingDividesTheYearsRateByTheDaysOfTheCalendarYear() {
    assertThat(DAILY_HOLDING.dailyAmount(HOLDING_VALUE, ANNUAL_RATE, LocalDate.of(2026, 11, 24)))
        .isEqualByComparingTo("1.3698630137");
  }

  @Test
  void theDailyHoldingDividesByThreeHundredSixtySixInALeapYear() {
    assertThat(DAILY_HOLDING.dailyAmount(HOLDING_VALUE, ANNUAL_RATE, LocalDate.of(2028, 2, 29)))
        .isEqualByComparingTo("1.3661202186");
  }

  @Test
  void theMonthEndHoldingSpreadsThirtyThreeHundredSixtiethsOfTheRateOverTheMonthsDays() {
    assertThat(
            MONTH_END_HOLDING.dailyAmount(HOLDING_VALUE, ANNUAL_RATE, LocalDate.of(2026, 11, 24)))
        .isEqualByComparingTo("1.3888888889");
  }

  @Test
  void theMonthEndHoldingAddsUpToATwelfthOfTheRateInFebruaryToo() {
    var february = YearMonth.of(2026, 2);

    var monthTotal =
        february
            .atDay(1)
            .datesUntil(february.plusMonths(1).atDay(1))
            .map(day -> MONTH_END_HOLDING.dailyAmount(HOLDING_VALUE, ANNUAL_RATE, day))
            .reduce(ZERO, BigDecimal::add);

    assertThat(monthTotal).isEqualByComparingTo("41.6666666668");
  }

  @Test
  void theQuarterlyAverageSpreadsAQuarterOfTheRateOverTheQuartersDays() {
    assertThat(
            QUARTER_AVERAGE_MONTH_END.dailyAmount(
                HOLDING_VALUE, ANNUAL_RATE, LocalDate.of(2026, 11, 24)))
        .isEqualByComparingTo("1.3586956522");
  }

  @Test
  void theQuarterlyAverageCountsTheDaysOfItsOwnQuarter() {
    assertThat(
            QUARTER_AVERAGE_MONTH_END.dailyAmount(
                HOLDING_VALUE, ANNUAL_RATE, LocalDate.of(2026, 2, 15)))
        .isEqualByComparingTo("1.3888888889");
  }

  @Test
  void aZeroRateAccruesNothing() {
    assertThat(DAILY_HOLDING.dailyAmount(HOLDING_VALUE, ZERO, LocalDate.of(2026, 11, 24)))
        .isEqualByComparingTo(ZERO);
  }

  @Test
  void theDailyHoldingCountsTradeDateUnitsAndThePeriodEndMethodsSettledUnits() {
    assertThat(UnitsBasis.of(DAILY_HOLDING)).isEqualTo(TRADE_DATE);
    assertThat(UnitsBasis.of(MONTH_END_HOLDING)).isEqualTo(SETTLED);
    assertThat(UnitsBasis.of(QUARTER_AVERAGE_MONTH_END)).isEqualTo(SETTLED);
  }
}
