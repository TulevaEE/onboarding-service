package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FlowWindowReader {

  private static final int FIRST_BUSINESS_DAY = 1;

  private final RegistrarCashFlowRepository registrarCashFlows;
  private final BusinessDays businessDays;
  private final PublicHolidays publicHolidays;

  Optional<FlowWindow> everyCompleteMonthThrough(TulevaFund fund, YearMonth lastMonth) {
    return registrarCashFlows
        .findFirstCashBookingDate(fund)
        .map(this::firstMonthTheLedgerHoldsInFull)
        .filter(firstMonth -> !firstMonth.isAfter(lastMonth))
        .map(firstMonth -> read(fund, firstMonth, lastMonth));
  }

  BusinessDayOutflows businessDayOutflows(TulevaFund fund, FlowWindow window) {
    var from = window.firstMonth().atDay(1);
    var through = window.lastMonth().atEndOfMonth();
    var businessDaysInWindow =
        Stream.iterate(from, day -> !day.isAfter(through), day -> day.plusDays(1))
            .filter(publicHolidays::isWorkingDay)
            .toList();
    var lastBusinessDay = businessDaysInWindow.getLast();
    var outflowByBusinessDay =
        registrarCashFlows.findPayouts(fund, from, through).stream()
            .filter(payout -> PayoutClass.of(payout.reason()).isOperating())
            .collect(
                groupingBy(
                    payout -> businessDayOf(payout.bookingDate(), lastBusinessDay),
                    reducing(ZERO, RegistrarPayout::amount, BigDecimal::add)));
    return new BusinessDayOutflows(
        businessDaysInWindow.stream()
            .map(day -> outflowByBusinessDay.getOrDefault(day, ZERO).setScale(2, HALF_UP))
            .toList());
  }

  int outgoingEntriesStillInSuspense(TulevaFund fund, FlowWindow window) {
    return registrarCashFlows.countOutgoingEntriesStillInSuspense(
        fund, window.firstMonth().atDay(1), window.lastMonth().atEndOfMonth());
  }

  private LocalDate businessDayOf(LocalDate bookingDate, LocalDate lastBusinessDay) {
    if (publicHolidays.isWorkingDay(bookingDate)) {
      return bookingDate;
    }
    var next = publicHolidays.nextWorkingDay(bookingDate);
    return next.isAfter(lastBusinessDay) ? lastBusinessDay : next;
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
