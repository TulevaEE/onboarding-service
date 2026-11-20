package ee.tuleva.onboarding.investment.check.rebalance;

import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record RebalanceOrdersAwaitingExecution(List<SentOrder> orders) {

  private static final int
      CUSTODIAN_CONFIRMS_AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_WITHIN_WORKING_DAYS = 2;

  record SentOrder(LocalDate sentOn, @Nullable LocalDate expectedSettlementDate) {}

  Optional<LocalDate> firstSentOn() {
    return orders.stream().map(SentOrder::sentOn).min(naturalOrder());
  }

  Optional<LocalDate> awaitedThrough(LocalDate deadline, TradingCalendar calendar) {
    return orders.stream()
        .filter(order -> !order.sentOn().isAfter(deadline))
        .map(
            order ->
                Optional.ofNullable(order.expectedSettlementDate())
                    .orElseGet(
                        () ->
                            calendar.addBusinessDays(
                                deadline,
                                CUSTODIAN_CONFIRMS_AN_ORDER_WITHOUT_A_SETTLEMENT_DATE_WITHIN_WORKING_DAYS)))
        .max(naturalOrder());
  }
}
