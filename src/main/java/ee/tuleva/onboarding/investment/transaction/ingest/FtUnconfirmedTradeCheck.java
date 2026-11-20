package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.idsOfOrdersWithAConfirmation;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NullMarked
@Slf4j
@Component
@RequiredArgsConstructor
class FtUnconfirmedTradeCheck {

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final FtCancellationCheck cancellationCheck;
  private final FtSebExecutionCheck executionCheck;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final FtConfirmationDigest digest;
  private final Clock clock;

  @Value("${transaction-registry.ft-confirmation.alerts-from}")
  private final LocalDate alertsFrom;

  @Value("${transaction-registry.ft-confirmation.unconfirmed-lookback-days}")
  private final int lookbackDays;

  void check(List<FtConfirmation> confirmations, String actor) {
    LocalDate firstTradeDate = firstTradeDateToCheck();
    List<TransactionOrder> ftOrders =
        orderRepository.findByOrderVenueAndOrderTimestampGreaterThanEqual(
            OrderVenue.FT, firstTradeDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    List<FtConfirmation> ofTheseDays =
        confirmations.stream()
            .filter(confirmation -> !confirmation.tradeDate().isBefore(firstTradeDate))
            .toList();
    Set<Long> confirmedOrderIds =
        idsOfOrdersWithAConfirmation(
            ftOrders,
            FtCorrections.among(ofTheseDays)
                .standingConfirmations(cancellationCheck::awaitingReplacement));
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

  private LocalDate firstTradeDateToCheck() {
    LocalDate lookbackStart = LocalDate.now(clock).minusDays(lookbackDays);
    return lookbackStart.isAfter(alertsFrom) ? lookbackStart : alertsFrom;
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
        ? FtExecutedTrade.confirmedByFt(order, executed)
        : executionCheck.unconfirmed(order, executed);
  }

  private FtCheckOutcome record(TransactionOrder order, FtExecutedTrade trade, String actor) {
    try {
      auditRecorder.recordOutcome(order, trade, actor);
    } catch (RuntimeException e) {
      log.error(
          "FT trade outcome could not be recorded: orderUuid={}, error={}",
          order.getOrderUuid(),
          e.getMessage(),
          e);
    }
    return trade;
  }
}
