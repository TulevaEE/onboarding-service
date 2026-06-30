package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ExecutionPriceConsistencyChecker {

  Optional<ExecutionPriceConsistencyEvent> check(
      TransactionOrder order, List<TransactionExecution> executions, BigDecimal tolerance) {
    List<BigDecimal> prices =
        executions.stream()
            .map(TransactionExecution::getUnitPrice)
            .filter(Objects::nonNull)
            .filter(price -> price.signum() > 0)
            .toList();
    if (prices.size() < 2) {
      return Optional.empty();
    }
    BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElseThrow();
    BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElseThrow();
    BigDecimal relativeSpread = max.subtract(min).divide(min, MathContext.DECIMAL64);
    boolean withinTolerance = relativeSpread.compareTo(tolerance) <= 0;
    if (withinTolerance) {
      return Optional.empty();
    }
    return Optional.of(
        new ExecutionPriceConsistencyEvent(
            order.getId(), order.getInstrumentIsin(), min, max, relativeSpread, tolerance, null));
  }
}
