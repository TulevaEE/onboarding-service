package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final TransactionSettlementRepository settlementRepository;
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
    Set<String> paidReferences = paidReferencesSince(since);
    List<DroppedTrade> candidates =
        orders.stream()
            .flatMap(
                order ->
                    unpaidPiecesOffTheList(
                        order,
                        executionsByOrder.getOrDefault(order.getId(), List.of()),
                        listing,
                        paidReferences))
            .toList();
    return bankStatementArrival.keepThoseWhoseStatementIsIn(candidates, listing.asOfDate());
  }

  private Stream<DroppedTrade> unpaidPiecesOffTheList(
      TransactionOrder order,
      List<TransactionExecution> executions,
      PendingListing listing,
      Set<String> paidReferences) {
    if (listing.mayStillList(order, executions)) {
      return Stream.empty();
    }
    return executions.stream()
        .flatMap(
            execution -> unpaid(order, execution, listing.asOfDate(), paidReferences).stream());
  }

  private boolean isDatedByTheBank(TransactionOrder order) {
    return order.getOrderStatus() == SETTLED
        && settlementRepository
            .findByOrderId(order.getId())
            .filter(settlement -> settlement.getSource() == BANK_STATEMENT)
            .isPresent();
  }

  private Set<String> paidReferencesSince(LocalDate since) {
    return tradeSettlementBookings.bookedSince(since).stream()
        .map(TradeSettlementBooking::brokerReference)
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private Optional<DroppedTrade> unpaid(
      TransactionOrder order,
      TransactionExecution execution,
      LocalDate asOfDate,
      Set<String> paidReferences) {
    String reference = execution.getBrokerTransactionId();
    LocalDate settlementDate = execution.getScheduledSettlementDate();
    if (reference == null || settlementDate == null || paidReferences.contains(reference)) {
      return Optional.empty();
    }
    if (settlementDate.isBefore(properties.alertsFrom()) || asOfDate.isBefore(settlementDate)) {
      return Optional.empty();
    }
    return Optional.of(new DroppedTrade(order, reference, settlementDate));
  }
}
