package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtOrderSelection(TransactionOrder order, boolean ambiguous, int matchCount) {

  private static final BigDecimal QUANTITY_TOLERANCE = BigDecimal.ONE;

  static FtOrderSelection among(List<TransactionOrder> candidates, FtConfirmation confirmation) {
    if (candidates.size() == 1) {
      return new FtOrderSelection(candidates.get(0), false, 1);
    }
    List<TransactionOrder> quantityMatches =
        candidates.stream().filter(order -> matchesQuantity(order, confirmation)).toList();
    if (quantityMatches.size() == 1) {
      return new FtOrderSelection(quantityMatches.get(0), false, 1);
    }
    if (quantityMatches.size() > 1) {
      return new FtOrderSelection(oldest(quantityMatches), true, quantityMatches.size());
    }
    return new FtOrderSelection(oldest(candidates), true, candidates.size());
  }

  static List<TransactionOrder> liveOrders(List<TransactionOrder> candidates) {
    return candidates.stream().filter(order -> !order.isCancelledOrDiscarded()).toList();
  }

  static boolean matchesOnlyAWithdrawnOrder(
      FtConfirmation confirmation,
      List<TransactionOrder> candidates,
      List<TransactionOrder> liveOrders) {
    return liveOrders.stream().noneMatch(order -> matchesQuantity(order, confirmation))
        && candidates.stream().anyMatch(order -> matchesQuantity(order, confirmation));
  }

  static boolean matchesQuantity(TransactionOrder order, FtConfirmation confirmation) {
    return hasQuantity(order, confirmation.quantity());
  }

  static boolean hasQuantity(TransactionOrder order, BigDecimal quantity) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    return orderQuantity != null && withinQuantityTolerance(quantity, orderQuantity);
  }

  static boolean withinQuantityTolerance(BigDecimal ftQuantity, BigDecimal quantity) {
    return ftQuantity.subtract(quantity).abs().compareTo(QUANTITY_TOLERANCE) <= 0;
  }

  private static TransactionOrder oldest(List<TransactionOrder> orders) {
    return orders.stream().min(comparing(TransactionOrder::getId)).orElseThrow();
  }
}
