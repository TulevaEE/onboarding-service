package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.TransactionCost;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionCostWindowTest {

  private static final BigDecimal COMMISSIONS = new BigDecimal("10");
  private static final BigDecimal AVERAGE_AUM = new BigDecimal("1000000");
  private static final LocalDate LEAP_MONTH_END = LocalDate.of(2028, 2, 29);
  private static final LocalDate TRAILING_YEAR_START = LocalDate.of(2027, 3, 1);

  @Test
  void aFullTrailingYearThatHoldsALeapDay_isTakenAtItsObservedRate() {
    var window =
        new TransactionCostWindow(
            TRAILING_YEAR_START, LEAP_MONTH_END, COMMISSIONS, AVERAGE_AUM, List.of());

    assertThat(window.annualised(TRAILING_YEAR_START))
        .isEqualTo(
            new TransactionCost(
                new BigDecimal("0.00001000"),
                TRAILING_YEAR_START,
                LEAP_MONTH_END,
                COMMISSIONS,
                AVERAGE_AUM,
                List.of(),
                List.of()));
  }

  @Test
  void aFundOneDayShortOfAFullYear_isStillAnnualisedOverItsOwnLife() {
    var monthEnd = LocalDate.of(2027, 1, 31);
    var dayAfterTheTrailingYearStart = LocalDate.of(2026, 2, 2);
    var window =
        new TransactionCostWindow(
            dayAfterTheTrailingYearStart, monthEnd, COMMISSIONS, AVERAGE_AUM, List.of());

    assertThat(window.annualised(LocalDate.of(2026, 2, 1)))
        .isEqualTo(
            new TransactionCost(
                new BigDecimal("0.00001003"),
                dayAfterTheTrailingYearStart,
                monthEnd,
                COMMISSIONS,
                AVERAGE_AUM,
                List.of(),
                List.of()));
  }

  @Test
  void aFundYoungerThanAYear_isAnnualisedOverItsOwnLifeOnA365DayYear_countingBothEnds() {
    var inception = LocalDate.of(2028, 1, 30);
    var window =
        new TransactionCostWindow(inception, LEAP_MONTH_END, COMMISSIONS, AVERAGE_AUM, List.of());

    assertThat(window.annualised(TRAILING_YEAR_START).rate()).isEqualByComparingTo("0.00011774");
  }
}
