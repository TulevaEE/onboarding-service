package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import ee.tuleva.onboarding.ledger.RegistrarContribution;
import ee.tuleva.onboarding.ledger.RegistrarPayout;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

record FlowWindow(List<MonthlyFlows> months) {

  static FlowWindow zeroFilled(
      YearMonth firstMonth,
      YearMonth lastMonth,
      List<RegistrarContribution> contributions,
      List<RegistrarPayout> payouts) {
    var inflowByMonth =
        contributions.stream()
            .collect(
                groupingBy(
                    contribution -> YearMonth.from(contribution.bookingDate()),
                    reducing(ZERO, RegistrarContribution::amount, BigDecimal::add)));
    var payoutsByMonth =
        payouts.stream().collect(groupingBy(payout -> YearMonth.from(payout.bookingDate())));
    return new FlowWindow(
        Stream.iterate(firstMonth, month -> !month.isAfter(lastMonth), month -> month.plusMonths(1))
            .map(
                month ->
                    MonthlyFlows.of(
                        month,
                        inflowByMonth.getOrDefault(month, ZERO),
                        payoutsByMonth.getOrDefault(month, List.of())))
            .toList());
  }

  YearMonth firstMonth() {
    return months.getFirst().month();
  }

  YearMonth lastMonth() {
    return months.getLast().month();
  }

  MonthlyFlows lastMonthFlows() {
    return months.getLast();
  }

  int depth() {
    return months.size();
  }

  List<BigDecimal> operatingOutflows() {
    return months.stream().map(MonthlyFlows::operatingOutflow).toList();
  }

  List<BigDecimal> inflows() {
    return months.stream().map(MonthlyFlows::inflow).toList();
  }

  int unrecognisedPayouts() {
    return months.stream().mapToInt(MonthlyFlows::unrecognisedPayouts).sum();
  }

  BigDecimal unrecognisedOutflow() {
    return months.stream().map(MonthlyFlows::unrecognisedOutflow).reduce(ZERO, BigDecimal::add);
  }
}
