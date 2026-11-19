package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.matchesQuantity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.MaximumWeightAssignment;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtCorrections(List<FtConfirmation> confirmations) {

  static FtCorrections among(List<FtConfirmation> confirmations) {
    return new FtCorrections(
        confirmations.stream()
            .filter(Objects::nonNull)
            .filter(confirmation -> !confirmation.suppressed())
            .toList());
  }

  boolean isVoided(FtConfirmation confirmation) {
    return confirmations.stream().anyMatch(confirmation::isCancelledBy);
  }

  boolean newConfirmationsCoverEveryOrder(
      FtConfirmation cancellation, List<TransactionOrder> liveOrders) {
    List<FtConfirmation> newConfirmations = newConfirmationsAfter(cancellation);
    List<TransactionOrder> ordersOfTheCancelledQuantity =
        liveOrders.stream().filter(order -> matchesQuantity(order, cancellation)).toList();
    List<TransactionOrder> ordersToCover =
        ordersOfTheCancelledQuantity.isEmpty() ? liveOrders : ordersOfTheCancelledQuantity;
    List<TransactionOrder> otherOrders =
        liveOrders.stream().filter(order -> !ordersToCover.contains(order)).toList();
    return !ordersToCover.isEmpty()
        && ordersWithTheirOwnConfirmation(liveOrders, newConfirmations)
                - ordersWithTheirOwnConfirmation(otherOrders, newConfirmations)
            >= ordersToCover.size();
  }

  private List<FtConfirmation> newConfirmationsAfter(FtConfirmation cancellation) {
    return confirmations.stream()
        .filter(confirmation -> confirmation.isNewConfirmationAfter(cancellation))
        .filter(confirmation -> !isVoided(confirmation))
        .collect(
            toMap(
                FtConfirmationAuditRecorder::dedupKey,
                confirmation -> confirmation,
                (first, sameTrade) -> first,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private static int ordersWithTheirOwnConfirmation(
      List<TransactionOrder> orders, List<FtConfirmation> newConfirmations) {
    if (orders.isEmpty() || newConfirmations.isEmpty()) {
      return 0;
    }
    long[][] quantityMatches =
        orders.stream()
            .map(
                order ->
                    newConfirmations.stream()
                        .mapToLong(confirmation -> matchesQuantity(order, confirmation) ? 1 : 0)
                        .toArray())
            .toArray(long[][]::new);
    return MaximumWeightAssignment.columnsOfRows(quantityMatches, newConfirmations.size()).size();
  }
}
