package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DroppedTradeFinder {

  private static final int ORDER_LOOKBACK_DAYS = 60;

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final TradeSettlementBookings tradeSettlementBookings;
  private final BankStatementArrival bankStatementArrival;
  private final SebTradeListingCheckProperties properties;

  DroppedTrades find(PendingListing listing) {
    LocalDate since = listing.asOfDate().minusDays(ORDER_LOOKBACK_DAYS);
    List<TransactionOrder> orders =
        orderRepository
            .findByOrderStatusInAndOrderTimestampSince(
                List.of(EXECUTED, SETTLED), since.atStartOfDay(UTC).toInstant())
            .stream()
            .filter(order -> !isDatedByTheBank(order))
            .toList();
    if (orders.isEmpty()) {
      return new DroppedTrades(List.of(), Set.of());
    }
    Map<Long, List<TransactionExecution>> executionsByOrder =
        executionRepository
            .findByOrderIdIn(orders.stream().map(TransactionOrder::getId).toList())
            .stream()
            .collect(groupingBy(TransactionExecution::getOrderId));
    List<DroppedTrade> offTheList =
        orders.stream()
            .flatMap(
                order ->
                    piecesOffTheList(
                        order, executionsByOrder.getOrDefault(order.getId(), List.of()), listing))
            .toList();
    DroppedTrades withTheStatementIn =
        bankStatementArrival.keepThoseWhoseStatementIsIn(offTheList, listing.asOfDate());
    BookingsByReference bookings =
        new BookingsByReference(tradeSettlementBookings.bookedSince(since), Map.of());
    return new DroppedTrades(
        withTheStatementIn.trades().stream()
            .filter(trade -> !bookings.paysFor(trade.order(), trade.reference()))
            .toList(),
        withTheStatementIn.fundsNotChecked());
  }

  private Stream<DroppedTrade> piecesOffTheList(
      TransactionOrder order, List<TransactionExecution> executions, PendingListing listing) {
    return executions.stream()
        .flatMap(execution -> dueSinceAlertsFrom(order, execution).stream())
        .filter(trade -> !listing.mayStillListPiece(order, trade.reference()))
        .filter(trade -> !listing.asOfDate().isBefore(trade.settlementDate()));
  }

  private boolean isDatedByTheBank(TransactionOrder order) {
    return order.getOrderStatus() == SETTLED && settlementService.isSettledByTheBank(order);
  }

  private Optional<DroppedTrade> dueSinceAlertsFrom(
      TransactionOrder order, TransactionExecution execution) {
    String reference = execution.getBrokerTransactionId();
    LocalDate settlementDate = execution.getScheduledSettlementDate();
    if (reference == null
        || settlementDate == null
        || settlementDate.isBefore(properties.alertsFrom())) {
      return Optional.empty();
    }
    return Optional.of(new DroppedTrade(order, reference, settlementDate));
  }
}
