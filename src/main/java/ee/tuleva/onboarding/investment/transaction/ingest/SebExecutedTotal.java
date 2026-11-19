package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.BigDecimal.ZERO;
import static java.math.MathContext.DECIMAL64;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SebExecutedTotal(BigDecimal quantity, BigDecimal averagePrice) {

  static Map<Long, SebExecutedTotal> byOrderId(List<TransactionExecution> executions) {
    return executions.stream()
        .flatMap(execution -> Fill.of(execution).stream())
        .collect(groupingBy(Fill::orderId, collectingAndThen(toList(), SebExecutedTotal::of)));
  }

  private static SebExecutedTotal of(List<Fill> fills) {
    BigDecimal quantity = fills.stream().map(Fill::quantity).reduce(ZERO, BigDecimal::add);
    BigDecimal consideration =
        fills.stream().map(Fill::consideration).reduce(ZERO, BigDecimal::add);
    return new SebExecutedTotal(
        quantity, consideration.divide(quantity, DECIMAL64).stripTrailingZeros());
  }

  private record Fill(Long orderId, BigDecimal quantity, BigDecimal price) {

    static Optional<Fill> of(TransactionExecution execution) {
      BigDecimal quantity = execution.getExecutedQuantity();
      BigDecimal price = execution.getUnitPrice();
      if (quantity == null || price == null || quantity.signum() <= 0) {
        return Optional.empty();
      }
      return Optional.of(new Fill(execution.getOrderId(), quantity, price));
    }

    BigDecimal consideration() {
      return quantity.multiply(price);
    }
  }
}
