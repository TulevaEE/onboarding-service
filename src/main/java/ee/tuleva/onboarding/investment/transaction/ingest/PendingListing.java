package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record PendingListing(
    LocalDate asOfDate,
    Set<UUID> clientRefs,
    Set<String> ourRefs,
    Set<String> isinsOfRowsWithoutReferences,
    Set<UUID> clientRefsOfRowsWithoutOurRef) {

  boolean settlesOn(
      LocalDate bookingDate, TransactionOrder order, List<TransactionExecution> executions) {
    return !asOfDate.isBefore(bookingDate) && !mayStillList(order, executions);
  }

  boolean mayStillListPiece(TransactionOrder order, String reference) {
    return isinsOfRowsWithoutReferences.contains(order.getInstrumentIsin())
        || ourRefs.contains(reference)
        || clientRefsOfRowsWithoutOurRef.contains(order.getOrderUuid());
  }

  boolean mayStillList(TransactionOrder order, List<TransactionExecution> executions) {
    return isinsOfRowsWithoutReferences.contains(order.getInstrumentIsin())
        || clientRefs.contains(order.getOrderUuid())
        || executions.stream()
            .map(TransactionExecution::getBrokerTransactionId)
            .anyMatch(ourRefs::contains);
  }
}
