package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.idsOfOrdersWithAConfirmation;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtUnconfirmedTradeCheck {

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final FtSebExecutionCheck executionCheck;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final FtConfirmationDigest digest;

  @Value("${transaction-registry.ft-confirmation.alerts-from}")
  private final LocalDate alertsFrom;

  void check(List<FtConfirmation> confirmations, String actor) {
    List<TransactionOrder> ftOrders = ftOrdersSinceAlertsFrom();
    Set<Long> confirmedOrderIds =
        idsOfOrdersWithAConfirmation(
            ftOrders, FtCorrections.among(confirmations).standingConfirmations());
    List<TransactionOrder> overdue =
        ftOrders.stream()
            .filter(order -> !order.isCancelledOrDiscarded())
            .filter(executionCheck::confirmationOverdue)
            .toList();
    Map<Long, SebExecutedTotal> executed = SebExecutedTotal.byOrderId(executionsOf(overdue));
    List<FtCheckOutcome> outcomes =
        overdue.stream()
            .flatMap(
                order ->
                    Optional.ofNullable(executed.get(order.getId()))
                        .map(total -> record(order, trade(order, total, confirmedOrderIds), actor))
                        .stream())
            .toList();
    digest.publish(outcomes);
  }

  private List<TransactionOrder> ftOrdersSinceAlertsFrom() {
    return orderRepository
        .findByOrderStatusInAndOrderTimestampSince(
            List.of(OrderStatus.values()), alertsFrom.atStartOfDay(ZoneOffset.UTC).toInstant())
        .stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.FT)
        .toList();
  }

  private List<TransactionExecution> executionsOf(List<TransactionOrder> orders) {
    if (orders.isEmpty()) {
      return List.of();
    }
    return executionRepository.findByOrderIdIn(
        orders.stream().map(TransactionOrder::getId).toList());
  }

  private FtExecutedTrade trade(
      TransactionOrder order, SebExecutedTotal executed, Set<Long> confirmedOrderIds) {
    return confirmedOrderIds.contains(order.getId())
        ? executionCheck.confirmed(order, executed)
        : executionCheck.unconfirmed(order, executed);
  }

  private FtCheckOutcome record(TransactionOrder order, FtExecutedTrade trade, String actor) {
    auditRecorder.recordOutcome(order, trade, actor);
    return trade;
  }
}
