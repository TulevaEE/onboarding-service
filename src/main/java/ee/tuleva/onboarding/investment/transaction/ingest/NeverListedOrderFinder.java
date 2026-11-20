package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static java.time.ZoneOffset.UTC;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NeverListedOrderFinder {

  private static final int ORDER_LOOKBACK_DAYS = 60;

  private final TransactionOrderRepository orderRepository;
  private final OverdueSettlementDetector overdueSettlementDetector;
  private final SebTradeListingCheckProperties properties;

  List<TransactionOrder> find(PendingListing listing) {
    LocalDate since = listing.asOfDate().minusDays(ORDER_LOOKBACK_DAYS);
    return orderRepository
        .findByOrderStatusInAndOrderTimestampSince(
            List.of(SENT), since.atStartOfDay(UTC).toInstant())
        .stream()
        .filter(order -> !listing.mayStillList(order, List.of()))
        .filter(order -> isPastItsDeadline(order, listing.asOfDate()))
        .toList();
  }

  private boolean isPastItsDeadline(TransactionOrder order, LocalDate asOfDate) {
    LocalDate deadline = overdueSettlementDetector.sentDeadline(order);
    return deadline != null
        && !deadline.isBefore(properties.alertsFrom())
        && !asOfDate.isBefore(deadline);
  }
}
