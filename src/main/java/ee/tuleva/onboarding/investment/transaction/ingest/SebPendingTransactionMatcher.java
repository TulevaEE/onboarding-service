package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebPendingTransactionMatcher {

  private final TransactionOrderRepository orderRepository;

  Optional<TransactionOrder> match(SebPendingTransactionRow row) {
    if (row.clientRef() == null) {
      return Optional.empty();
    }
    return orderRepository
        .findByOrderUuid(row.clientRef())
        .filter(order -> isMatchable(order.getOrderStatus()));
  }

  static boolean isMatchable(OrderStatus status) {
    return status != OrderStatus.CANCELLED
        && status != OrderStatus.DRAFT
        && status != OrderStatus.DISCARDED;
  }
}
