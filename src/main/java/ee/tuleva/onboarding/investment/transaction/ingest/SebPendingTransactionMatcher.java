package ee.tuleva.onboarding.investment.transaction.ingest;

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
    return orderRepository.findByOrderUuid(row.clientRef());
  }
}
