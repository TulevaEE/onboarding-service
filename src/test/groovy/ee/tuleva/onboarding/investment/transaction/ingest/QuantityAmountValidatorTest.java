package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind.FUND_BUY_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuantityAmountValidatorTest {

  private final QuantityAmountValidator validator =
      new QuantityAmountValidator(new TransactionMatchingProperties(null, null, null, null));

  @Test
  void validate_fundBuyDeltaExactlyTwoPercentOfOrderedAmount_returnsMismatch() {
    // delta 2000.00: /ordered = 2.000% (>= 2% → mismatch), /executed = 1.961% (would pass)
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    Optional<QuantityAmountMismatchEvent> mismatch = validator.validate(order, row);

    assertThat(mismatch).isPresent();
    QuantityAmountMismatchEvent event = mismatch.get();
    assertThat(event.kind()).isEqualTo(FUND_BUY_AMOUNT);
    assertThat(event.expected()).isEqualByComparingTo("100000.00");
    assertThat(event.actual()).isEqualByComparingTo("102000.00");
    assertThat(event.delta()).isEqualByComparingTo("2000.00");
  }

  @Test
  void validate_fundBuyDeltaWithinTwoPercentOfOrderedAmount_returnsEmpty() {
    // delta 1970.00: /ordered = 1.970% (< 2% → ok), /executed = 2.010% (would mismatch)
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("98030.00"));

    assertThat(validator.validate(order, row)).isEmpty();
  }

  @Test
  void withinTolerance_fundBuyUsesOrderedAmountAsDenominator() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));

    assertThat(validator.withinTolerance(order, fundBuyRow(new BigDecimal("102000.00")))).isFalse();
    assertThat(validator.withinTolerance(order, fundBuyRow(new BigDecimal("98030.00")))).isTrue();
  }

  @Test
  void withinNearMiss_fundBuyUsesOrderedAmountAsDenominator() {
    // near-miss band = 2% × 5 = 10% of ordered amount
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));

    // delta 10000.00: /ordered = 10.000% (>= 10% → outside), /executed = 9.091% (would pass)
    assertThat(validator.withinNearMiss(order, fundBuyRow(new BigDecimal("110000.00")))).isFalse();
    assertThat(validator.withinNearMiss(order, fundBuyRow(new BigDecimal("109000.00")))).isTrue();
  }

  @Test
  void validate_fundBuyWithNullOrderAmount_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(null);
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.validate(order, row)).isEmpty();
  }

  @Test
  void withinTolerance_fundBuyWithNullOrderAmount_returnsFalse() {
    TransactionOrder order = fundBuyOrder(null);
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.withinTolerance(order, row)).isFalse();
    assertThat(validator.withinNearMiss(order, row)).isFalse();
  }

  @Test
  void withinTolerance_fundBuyWithZeroOrderAmount_returnsFalseWithoutDividing() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.withinTolerance(order, row)).isFalse();
    assertThat(validator.withinNearMiss(order, row)).isFalse();
  }

  @Test
  void validate_fundBuyWithZeroOrderAmount_returnsMismatch() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    Optional<QuantityAmountMismatchEvent> mismatch = validator.validate(order, row);

    assertThat(mismatch).isPresent();
    assertThat(mismatch.get().expected()).isEqualByComparingTo("0.00");
    assertThat(mismatch.get().actual()).isEqualByComparingTo("102000.00");
  }

  @Test
  void validate_fundBuyWithNullRowTotal_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(null);

    assertThat(validator.validate(order, row)).isEmpty();
    assertThat(validator.withinTolerance(order, row)).isFalse();
  }

  private static TransactionOrder fundBuyOrder(BigDecimal orderAmount) {
    return TransactionOrder.builder()
        .id(1L)
        .fund(TKF100)
        .instrumentIsin("IE00BFG1TM61")
        .transactionType(BUY)
        .instrumentType(FUND)
        .orderAmount(orderAmount)
        .orderVenue(SEB)
        .orderStatus(SENT)
        .build();
  }

  private static SebPendingTransactionRow fundBuyRow(BigDecimal total) {
    return new SebPendingTransactionRow(
        null,
        "DLA0000000",
        "IE00BFG1TM61",
        null,
        null,
        total,
        null,
        total,
        BUY,
        null,
        null,
        "Tuleva Täiendav Kogumisfond",
        null,
        null);
  }
}
