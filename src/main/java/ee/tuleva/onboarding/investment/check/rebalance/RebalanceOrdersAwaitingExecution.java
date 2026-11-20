package ee.tuleva.onboarding.investment.check.rebalance;

import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record RebalanceOrdersAwaitingExecution(List<SentOrder> orders) {

  private static final int
      AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_SETTLES_WORKING_DAYS_AFTER_THE_DEADLINE = 2;
  private static final int THE_CUSTODIANS_REPORT_FOR_A_DAY_ARRIVES_WORKING_DAYS_LATER = 1;

  record SentOrder(LocalDate sentOn, @Nullable LocalDate expectedSettlementDate) {}

  Optional<LocalDate> firstSentOn() {
    return orders.stream().map(SentOrder::sentOn).min(naturalOrder());
  }

  Optional<LocalDate> awaitedThrough(LocalDate deadline, TradingCalendar calendar) {
    return orders.stream()
        .filter(order -> !order.sentOn().isAfter(deadline))
        .map(order -> settlementDate(order, deadline, calendar))
        .max(naturalOrder())
        .map(
            latestSettlementDate ->
                calendar.addBusinessDays(
                    latestSettlementDate,
                    THE_CUSTODIANS_REPORT_FOR_A_DAY_ARRIVES_WORKING_DAYS_LATER));
  }

  private static LocalDate settlementDate(
      SentOrder order, LocalDate deadline, TradingCalendar calendar) {
    return Optional.ofNullable(order.expectedSettlementDate())
        .orElseGet(
            () ->
                calendar.addBusinessDays(
                    deadline,
                    AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_SETTLES_WORKING_DAYS_AFTER_THE_DEADLINE));
  }
}
