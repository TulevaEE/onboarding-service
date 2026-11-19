package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.hasQuantity;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.matchesQuantity;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
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
    if (!ordersOfTheCancelledQuantity.isEmpty()) {
      return cover(ordersOfTheCancelledQuantity, newConfirmations);
    }
    return liveOrders.stream()
        .allMatch(order -> cover(ordersOfTheSameQuantityAs(order, liveOrders), newConfirmations));
  }

  private List<FtConfirmation> newConfirmationsAfter(FtConfirmation cancellation) {
    return confirmations.stream()
        .filter(confirmation -> confirmation.isNewConfirmationAfter(cancellation))
        .filter(confirmation -> !isVoided(confirmation))
        .toList();
  }

  private static List<TransactionOrder> ordersOfTheSameQuantityAs(
      TransactionOrder order, List<TransactionOrder> liveOrders) {
    BigDecimal quantity = order.getOrderQuantity();
    if (quantity == null) {
      return List.of(order);
    }
    return liveOrders.stream().filter(other -> hasQuantity(other, quantity)).toList();
  }

  private static boolean cover(
      List<TransactionOrder> orders, List<FtConfirmation> newConfirmations) {
    long confirmations =
        newConfirmations.stream()
            .filter(
                confirmation ->
                    orders.stream().anyMatch(order -> matchesQuantity(order, confirmation)))
            .map(FtConfirmationAuditRecorder::dedupKey)
            .distinct()
            .count();
    return confirmations > 0 && confirmations >= orders.size();
  }
}
