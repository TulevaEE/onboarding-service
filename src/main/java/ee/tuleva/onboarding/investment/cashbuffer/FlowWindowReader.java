package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FlowWindowReader {

  private static final int FIRST_BUSINESS_DAY = 1;

  private final RegistrarCashFlowRepository registrarCashFlows;
  private final BusinessDays businessDays;

  Optional<FlowWindow> everyCompleteMonthThrough(TulevaFund fund, YearMonth lastMonth) {
    return registrarCashFlows
        .findFirstCashBookingDate(fund)
        .map(this::firstMonthTheLedgerHoldsInFull)
        .filter(firstMonth -> !firstMonth.isAfter(lastMonth))
        .map(firstMonth -> read(fund, firstMonth, lastMonth));
  }

  private YearMonth firstMonthTheLedgerHoldsInFull(LocalDate firstCashBooking) {
    var month = YearMonth.from(firstCashBooking);
    var missedABookingDayOfThatMonth =
        firstCashBooking.isAfter(
            businessDays.nthBusinessDayOfMonth(firstCashBooking, FIRST_BUSINESS_DAY));
    return missedABookingDayOfThatMonth ? month.plusMonths(1) : month;
  }

  private FlowWindow read(TulevaFund fund, YearMonth firstMonth, YearMonth lastMonth) {
    var from = firstMonth.atDay(1);
    var through = lastMonth.atEndOfMonth();
    return FlowWindow.zeroFilled(
        firstMonth,
        lastMonth,
        registrarCashFlows.findContributions(fund, from, through),
        registrarCashFlows.findPayouts(fund, from, through));
  }
}
