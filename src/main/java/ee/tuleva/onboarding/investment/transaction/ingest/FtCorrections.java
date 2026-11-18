package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.hasQuantity;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.withinQuantityTolerance;

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
    if (liveOrders.stream().anyMatch(order -> hasQuantity(order, cancellation.quantity()))) {
      return coverTheOrdersOf(cancellation.quantity(), newConfirmations, liveOrders);
    }
    return liveOrders.stream()
        .allMatch(order -> coverTheOrdersLike(order, newConfirmations, liveOrders));
  }

  private List<FtConfirmation> newConfirmationsAfter(FtConfirmation cancellation) {
    return confirmations.stream()
        .filter(confirmation -> confirmation.isNewConfirmationAfter(cancellation))
        .filter(confirmation -> !isVoided(confirmation))
        .toList();
  }

  private static boolean coverTheOrdersLike(
      TransactionOrder order,
      List<FtConfirmation> newConfirmations,
      List<TransactionOrder> liveOrders) {
    BigDecimal quantity = order.getOrderQuantity();
    return quantity != null && coverTheOrdersOf(quantity, newConfirmations, liveOrders);
  }

  private static boolean coverTheOrdersOf(
      BigDecimal quantity,
      List<FtConfirmation> newConfirmations,
      List<TransactionOrder> liveOrders) {
    long orders = liveOrders.stream().filter(order -> hasQuantity(order, quantity)).count();
    long confirmations =
        newConfirmations.stream()
            .filter(confirmation -> withinQuantityTolerance(quantity, confirmation.quantity()))
            .map(FtConfirmationAuditRecorder::dedupKey)
            .distinct()
            .count();
    return confirmations > 0 && confirmations >= orders;
  }
}
