package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record PendingListing(LocalDate asOfDate, Set<UUID> clientRefs, Set<String> ourRefs) {

  boolean settlesOn(
      LocalDate bookingDate, TransactionOrder order, List<TransactionExecution> executions) {
    return !asOfDate.isBefore(bookingDate)
        && !clientRefs.contains(order.getOrderUuid())
        && executions.stream()
            .map(TransactionExecution::getBrokerTransactionId)
            .noneMatch(ourRefs::contains);
  }
}
