package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.time.ZoneOffset.UTC;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class SettlementFromBankService {

  private final TradeSettlementBookings tradeSettlementBookings;
  private final TransactionOrderRepository orderRepository;
  private final TransactionMatchingPolicy matchingPolicy;
  private final LatestPendingReport latestPendingReport;
  private final OrderBankSettlement orderBankSettlement;

  BankSettlementRun settleOrdersBookedSince(LocalDate since) {
    return settleOrdersBookedSince(since, Map.of());
  }

  BankSettlementRun settleOrdersBookedSince(
      LocalDate since, Map<UUID, String> referencesOfOlderBookings) {
    List<TransactionOrder> orders =
        orderRepository.findByOrderStatusInAndOrderTimestampSince(
            List.of(EXECUTED, SETTLED), since.atStartOfDay(UTC).toInstant());
    PendingListing listing =
        latestPendingReport
            .listing()
            .orElseThrow(
                () ->
                    new IllegalStateException("No complete SEB pending report to settle against"));
    BookingsByReference bookings =
        new BookingsByReference(
            tradeSettlementBookings.bookedSince(since), referencesOfOlderBookings);
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    List<BankSettlementOutcome> outcomes =
        orders.stream().map(order -> settle(order, bookings, listing, matchingProperties)).toList();
    return new BankSettlementRun(
        count(outcomes, BankSettlementOutcome.SETTLED),
        count(outcomes, BankSettlementOutcome.SAME_DATE),
        count(outcomes, BankSettlementOutcome.NEW_DATE),
        count(outcomes, BankSettlementOutcome.NO_BANK_DATE),
        count(outcomes, BankSettlementOutcome.FAILED));
  }

  private BankSettlementOutcome settle(
      TransactionOrder order,
      BookingsByReference bookings,
      PendingListing listing,
      TransactionMatchingProperties matchingProperties) {
    try {
      return orderBankSettlement.settle(order.getId(), bookings, listing, matchingProperties);
    } catch (RuntimeException e) {
      log.error("Settling an order from the bank failed: orderId={}", order.getId(), e);
      return BankSettlementOutcome.FAILED;
    }
  }

  private static int count(List<BankSettlementOutcome> outcomes, BankSettlementOutcome outcome) {
    return (int) outcomes.stream().filter(outcome::equals).count();
  }
}
