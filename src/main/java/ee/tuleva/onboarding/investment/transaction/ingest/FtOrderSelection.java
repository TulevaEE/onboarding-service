package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.MaximumWeightAssignment;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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

  static Set<Integer> ordersWithTheirOwnConfirmation(
      List<TransactionOrder> orders, List<FtConfirmation> confirmations) {
    if (confirmations.isEmpty()) {
      return Set.of();
    }
    long[][] quantityMatches =
        orders.stream()
            .map(
                order ->
                    confirmations.stream()
                        .mapToLong(confirmation -> matchesQuantity(order, confirmation) ? 1 : 0)
                        .toArray())
            .toArray(long[][]::new);
    return MaximumWeightAssignment.columnsOfRows(quantityMatches, confirmations.size()).keySet();
  }

  static Set<Long> idsOfOrdersWithAConfirmation(
      List<TransactionOrder> orders, List<FtConfirmation> confirmations) {
    Map<TradeDay, List<FtConfirmation>> confirmationsByDay =
        confirmations.stream().collect(groupingBy(TradeDay::ofConfirmation));
    return orders.stream().collect(groupingBy(TradeDay::ofOrder)).entrySet().stream()
        .flatMap(
            day ->
                idsOfOrdersConfirmedThatDay(
                    day.getValue(), confirmationsByDay.getOrDefault(day.getKey(), List.of())))
        .collect(toSet());
  }

  private static Stream<Long> idsOfOrdersConfirmedThatDay(
      List<TransactionOrder> orders, List<FtConfirmation> confirmations) {
    if (orders.size() == 1 && !confirmations.isEmpty()) {
      return Stream.of(orders.getFirst().getId());
    }
    return ordersWithTheirOwnConfirmation(orders, confirmations).stream()
        .map(row -> orders.get(row).getId());
  }

  static LocalDate tradeDate(TransactionOrder order) {
    return LocalDate.ofInstant(requireNonNull(order.getOrderTimestamp()), ZoneOffset.UTC);
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

  private record TradeDay(TulevaFund fund, String isin, LocalDate date) {

    static TradeDay ofOrder(TransactionOrder order) {
      return new TradeDay(order.getFund(), order.getInstrumentIsin(), tradeDate(order));
    }

    static TradeDay ofConfirmation(FtConfirmation confirmation) {
      return new TradeDay(confirmation.fund(), confirmation.isin(), confirmation.tradeDate());
    }
  }
}
