package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionPriceConsistencyCheckerTest {

  private final ExecutionPriceConsistencyChecker checker = new ExecutionPriceConsistencyChecker();
  private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

  private static TransactionOrder order() {
    return TransactionOrder.builder().id(7L).instrumentIsin("IE000I9HGDZ3").build();
  }

  private static TransactionExecution executionAtPrice(String price) {
    return TransactionExecution.builder()
        .orderId(7L)
        .unitPrice(new BigDecimal(price))
        .source("SEB")
        .build();
  }

  @Test
  void noEventWhenFewerThanTwoPricedExecutions() {
    assertThat(checker.check(order(), List.of(executionAtPrice("9.99")), TOLERANCE)).isEmpty();
  }

  @Test
  void noEventWhenPricesWithinTolerance() {
    Optional<ExecutionPriceConsistencyEvent> result =
        checker.check(
            order(),
            List.of(executionAtPrice("10.00"), executionAtPrice("10.05")),
            new BigDecimal("0.01"));
    assertThat(result).isEmpty();
  }

  @Test
  void emitsEventWhenPricesDivergeBeyondTolerance() {
    Optional<ExecutionPriceConsistencyEvent> result =
        checker.check(
            order(),
            List.of(executionAtPrice("9.99"), executionAtPrice("10.40"), executionAtPrice("10.00")),
            TOLERANCE);

    assertThat(result).isPresent();
    ExecutionPriceConsistencyEvent event = result.orElseThrow();
    assertThat(event.orderId()).isEqualTo(7L);
    assertThat(event.isin()).isEqualTo("IE000I9HGDZ3");
    assertThat(event.minUnitPrice()).isEqualByComparingTo("9.99");
    assertThat(event.maxUnitPrice()).isEqualByComparingTo("10.40");
    assertThat(event.tolerance()).isEqualByComparingTo("0.01");
    assertThat(event.relativeSpread()).isGreaterThan(TOLERANCE);
  }

  @Test
  void ignoresNullAndNonPositivePrices() {
    TransactionExecution noPrice = TransactionExecution.builder().orderId(7L).source("SEB").build();
    TransactionExecution zeroPrice = executionAtPrice("0");
    assertThat(
            checker.check(
                order(), List.of(noPrice, zeroPrice, executionAtPrice("10.00")), TOLERANCE))
        .isEmpty();
  }
}
