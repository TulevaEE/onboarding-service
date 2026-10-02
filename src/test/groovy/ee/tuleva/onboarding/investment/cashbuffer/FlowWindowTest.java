package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_SWITCH;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.INHERITANCE;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.SECOND_PILLAR_EXIT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.UNRECOGNISED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.RegistrarContribution;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowWindowTest {

  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final YearMonth JULY = YearMonth.of(2026, 7);

  @Test
  void splitsEachMonthsPayoutsByClassAndKeepsCycleAndUnrecognisedOutOfTheOperatingOutflow() {
    var window =
        FlowWindow.zeroFilled(
            JUNE,
            JUNE,
            List.of(contribution("2026-06-02", "500.00"), contribution("2026-06-20", "250.00")),
            List.of(
                payout("2026-06-03", "10.00", FUND_PENSION),
                payout("2026-06-04", "20.00", ONE_OFF_WITHDRAWAL),
                payout("2026-06-05", "30.00", INHERITANCE),
                payout("2026-06-06", "400.00", FUND_SWITCH),
                payout("2026-06-07", "600.00", SECOND_PILLAR_EXIT),
                payout("2026-06-08", "7.00", UNRECOGNISED)));

    assertThat(window.months())
        .containsExactly(
            new MonthlyFlows(
                JUNE,
                new BigDecimal("750.00"),
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("7.00"),
                1));
    assertThat(window.operatingOutflows()).containsExactly(new BigDecimal("60.00"));
  }

  @Test
  void aMonthWithNoRegistrarRowsAtAllCountsAsZeroRatherThanDroppingOutOfTheWindow() {
    var window =
        FlowWindow.zeroFilled(
            MAY,
            JULY,
            List.of(contribution("2026-05-10", "100.00"), contribution("2026-07-10", "300.00")),
            List.of(payout("2026-05-11", "40.00", FUND_PENSION)));

    assertThat(window.firstMonth()).isEqualTo(MAY);
    assertThat(window.depth()).isEqualTo(3);
    assertThat(window.months().get(1)).isEqualTo(quiet(JUNE));
    assertThat(window.inflows())
        .containsExactly(
            new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("300.00"));
    assertThat(window.operatingOutflows())
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
  }

  @Test
  void aWindowWithNoFlowsAtAllIsEveryMonthZero() {
    var window = FlowWindow.zeroFilled(MAY, JUNE, List.of(), List.of());

    assertThat(window.months()).containsExactly(quiet(MAY), quiet(JUNE));
  }

  @Test
  void totalsTheUnrecognisedPayoutsAcrossTheWindow() {
    var window =
        FlowWindow.zeroFilled(
            MAY,
            JUNE,
            List.of(),
            List.of(
                payout("2026-05-11", "40.00", UNRECOGNISED),
                payout("2026-06-11", "2.50", UNRECOGNISED),
                payout("2026-06-12", "9.00", FUND_PENSION)));

    assertThat(window.unrecognisedPayouts()).isEqualTo(2);
    assertThat(window.unrecognisedOutflow()).isEqualByComparingTo("42.50");
  }

  private static MonthlyFlows quiet(YearMonth month) {
    var zero = new BigDecimal("0.00");
    return new MonthlyFlows(month, zero, zero, zero, zero, zero, 0);
  }

  private static RegistrarContribution contribution(String bookingDate, String amount) {
    return new RegistrarContribution(LocalDate.parse(bookingDate), new BigDecimal(amount));
  }

  private static RegistrarPayout payout(
      String bookingDate, String amount, RegistrarPayoutReason reason) {
    return new RegistrarPayout(LocalDate.parse(bookingDate), new BigDecimal(amount), reason);
  }
}
