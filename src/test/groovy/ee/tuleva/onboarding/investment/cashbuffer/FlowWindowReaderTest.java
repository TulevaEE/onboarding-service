package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_SWITCH;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.UNRECOGNISED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarContribution;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowWindowReaderTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  @Mock private RegistrarCashFlowRepository registrarCashFlows;
  @Spy private PublicHolidays publicHolidays = new PublicHolidays();
  @Spy private BusinessDays businessDays = new BusinessDays(new PublicHolidays());
  @InjectMocks private FlowWindowReader reader;

  @Test
  void aLedgerThatStartsOnTheFirstBusinessDayHoldsThatWholeMonth() {
    ledgerStartsOn(LocalDate.of(2026, 6, 1));
    flowsBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 30));

    var window = reader.everyCompleteMonthThrough(TUK75, SEPTEMBER).orElseThrow();

    assertThat(window.firstMonth()).isEqualTo(YearMonth.of(2026, 6));
    assertThat(window.depth()).isEqualTo(4);
    assertThat(window.inflows())
        .containsExactly(
            new BigDecimal("100.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"));
    assertThat(window.operatingOutflows())
        .containsExactly(
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("40.00"));
  }

  @Test
  void aMonthWhoseFirstDaysAreHolidaysAndAWeekendIsStillHeldInFullFromItsFirstBusinessDay() {
    ledgerStartsOn(LocalDate.of(2026, 5, 4));
    flowsBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30));

    var window = reader.everyCompleteMonthThrough(TUK75, SEPTEMBER).orElseThrow();

    assertThat(window.firstMonth()).isEqualTo(YearMonth.of(2026, 5));
    assertThat(window.depth()).isEqualTo(5);
  }

  @Test
  void aLedgerThatStartsMidMonthLeavesThatPartialMonthOutOfTheWindow() {
    ledgerStartsOn(LocalDate.of(2026, 6, 10));
    flowsBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));

    var window = reader.everyCompleteMonthThrough(TUK75, SEPTEMBER).orElseThrow();

    assertThat(window.firstMonth()).isEqualTo(YearMonth.of(2026, 7));
    assertThat(window.depth()).isEqualTo(3);
  }

  @Test
  void aLedgerThatStartedDuringTheReviewMonthHasNoCompleteMonthYet() {
    ledgerStartsOn(LocalDate.of(2026, 9, 2));

    assertThat(reader.everyCompleteMonthThrough(TUK75, SEPTEMBER)).isEmpty();
  }

  @Test
  void aFundWhoseCashTheLedgerNeverSawHasNoWindow() {
    given(registrarCashFlows.findFirstCashBookingDate(TUK75)).willReturn(Optional.empty());

    assertThat(reader.everyCompleteMonthThrough(TUK75, SEPTEMBER)).isEmpty();
  }

  @Test
  void operatingOutflowsAreTotalledPerBusinessDayWithAWeekendBookingOnTheNextOne() {
    var june = windowOf(YearMonth.of(2026, 6));
    given(
            registrarCashFlows.findPayouts(
                TUK75, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
        .willReturn(
            List.of(
                payout(LocalDate.of(2026, 6, 3), "100.00", FUND_PENSION),
                payout(LocalDate.of(2026, 6, 6), "50.00", ONE_OFF_WITHDRAWAL),
                payout(LocalDate.of(2026, 6, 8), "999.00", FUND_SWITCH),
                payout(LocalDate.of(2026, 6, 9), "777.00", UNRECOGNISED)));

    var outflows = reader.businessDayOutflows(TUK75, june).operatingOutflows();

    assertThat(outflows).hasSize(20);
    assertThat(outflows.subList(0, 7))
        .containsExactly(
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("100.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("50.00"),
            new BigDecimal("0.00"));
  }

  @Test
  void aBookingAfterTheWindowsLastBusinessDayStaysInTheWindowOnThatDay() {
    var may = windowOf(YearMonth.of(2026, 5));
    given(
            registrarCashFlows.findPayouts(
                TUK75, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
        .willReturn(List.of(payout(LocalDate.of(2026, 5, 31), "70.00", ONE_OFF_WITHDRAWAL)));

    var outflows = reader.businessDayOutflows(TUK75, may).operatingOutflows();

    assertThat(outflows.getLast()).isEqualByComparingTo("70.00");
  }

  private static FlowWindow windowOf(YearMonth month) {
    return FlowWindow.zeroFilled(month, month, List.of(), List.of());
  }

  private static RegistrarPayout payout(
      LocalDate bookingDate, String amount, RegistrarPayoutReason reason) {
    return new RegistrarPayout(bookingDate, new BigDecimal(amount), reason);
  }

  private void ledgerStartsOn(LocalDate firstBooking) {
    given(registrarCashFlows.findFirstCashBookingDate(TUK75)).willReturn(Optional.of(firstBooking));
  }

  private void flowsBetween(LocalDate from, LocalDate through) {
    given(registrarCashFlows.findContributions(TUK75, from, through))
        .willReturn(
            List.of(new RegistrarContribution(LocalDate.of(2026, 6, 3), new BigDecimal("100.00"))));
    given(registrarCashFlows.findPayouts(TUK75, from, through))
        .willReturn(
            List.of(
                new RegistrarPayout(
                    LocalDate.of(2026, 9, 3), new BigDecimal("40.00"), FUND_PENSION)));
  }
}
