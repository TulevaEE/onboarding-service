package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind.FUND_BUY_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuantityAmountValidatorTest {

  private static final TransactionMatchingProperties PROPERTIES =
      new TransactionMatchingProperties(null, null, null, null, null);

  private final QuantityAmountValidator validator = new QuantityAmountValidator();

  @Test
  void validate_fundBuyDeltaExactlyTwoPercentOfOrderedAmount_returnsMismatch() {
    // delta 2000.00: /ordered = 2.000% (>= 2% → mismatch), /executed = 1.961% (would pass)
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    Optional<QuantityAmountMismatchEvent> mismatch = validator.validate(order, row, PROPERTIES);

    assertThat(mismatch).isPresent();
    QuantityAmountMismatchEvent event = mismatch.get();
    assertThat(event.kind()).isEqualTo(FUND_BUY_AMOUNT);
    assertThat(event.expected()).isEqualByComparingTo("100000.00");
    assertThat(event.actual()).isEqualByComparingTo("102000.00");
    assertThat(event.delta()).isEqualByComparingTo("2000.00");
    assertThat(event.tolerance()).isEqualByComparingTo("0.02");
    assertThat(event.nearMissMultiplier()).isEqualByComparingTo("5");
  }

  @Test
  void validate_fundBuyDeltaWithinTwoPercentOfOrderedAmount_returnsEmpty() {
    // delta 1970.00: /ordered = 1.970% (< 2% → ok), /executed = 2.010% (would mismatch)
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("98030.00"));

    assertThat(validator.validate(order, row, PROPERTIES)).isEmpty();
  }

  @Test
  void withinTolerance_fundBuyUsesOrderedAmountAsDenominator() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));

    assertThat(
            validator.withinTolerance(order, fundBuyRow(new BigDecimal("102000.00")), PROPERTIES))
        .isFalse();
    assertThat(validator.withinTolerance(order, fundBuyRow(new BigDecimal("98030.00")), PROPERTIES))
        .isTrue();
  }

  @Test
  void withinNearMiss_fundBuyUsesOrderedAmountAsDenominator() {
    // near-miss band = 2% × 5 = 10% of ordered amount
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));

    // delta 10000.00: /ordered = 10.000% (>= 10% → outside), /executed = 9.091% (would pass)
    assertThat(validator.withinNearMiss(order, fundBuyRow(new BigDecimal("110000.00")), PROPERTIES))
        .isFalse();
    assertThat(validator.withinNearMiss(order, fundBuyRow(new BigDecimal("109000.00")), PROPERTIES))
        .isTrue();
  }

  @Test
  void validate_fundBuyWithNullOrderAmount_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(null);
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.validate(order, row, PROPERTIES)).isEmpty();
  }

  @Test
  void withinTolerance_fundBuyWithNullOrderAmount_returnsFalse() {
    TransactionOrder order = fundBuyOrder(null);
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.withinTolerance(order, row, PROPERTIES)).isFalse();
    assertThat(validator.withinNearMiss(order, row, PROPERTIES)).isFalse();
  }

  @Test
  void withinTolerance_fundBuyWithZeroOrderAmount_returnsFalseWithoutDividing() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    assertThat(validator.withinTolerance(order, row, PROPERTIES)).isFalse();
    assertThat(validator.withinNearMiss(order, row, PROPERTIES)).isFalse();
  }

  @Test
  void validate_fundBuyWithZeroOrderAmount_returnsMismatch() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));
    SebPendingTransactionRow row = fundBuyRow(new BigDecimal("102000.00"));

    Optional<QuantityAmountMismatchEvent> mismatch = validator.validate(order, row, PROPERTIES);

    assertThat(mismatch).isPresent();
    assertThat(mismatch.get().expected()).isEqualByComparingTo("0.00");
    assertThat(mismatch.get().actual()).isEqualByComparingTo("102000.00");
  }

  @Test
  void validate_fundBuyWithNullRowTotal_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    SebPendingTransactionRow row = fundBuyRow(null);

    assertThat(validator.validate(order, row, PROPERTIES)).isEmpty();
    assertThat(validator.withinTolerance(order, row, PROPERTIES)).isFalse();
  }

  @Test
  void validateCumulative_fundBuyUnderFill_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    var existing = List.of(execAmount("REF1", new BigDecimal("30000.00")));
    SebPendingTransactionRow row = fundBuyRowWithRef("REF2", new BigDecimal("30000.00"));

    assertThat(validator.validateCumulative(order, row, existing, PROPERTIES)).isEmpty();
  }

  @Test
  void validateCumulative_fundBuyOverFillBeyondTolerance_returnsMismatch() {
    // cumulative 105000 vs target 100000 = +5% (> 2% tolerance)
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    var existing = List.of(execAmount("REF1", new BigDecimal("100000.00")));
    SebPendingTransactionRow row = fundBuyRowWithRef("REF2", new BigDecimal("5000.00"));

    Optional<QuantityAmountMismatchEvent> mismatch =
        validator.validateCumulative(order, row, existing, PROPERTIES);

    assertThat(mismatch).isPresent();
    assertThat(mismatch.get().kind()).isEqualTo(FUND_BUY_AMOUNT);
    assertThat(mismatch.get().expected()).isEqualByComparingTo("100000.00");
    assertThat(mismatch.get().actual()).isEqualByComparingTo("105000.00");
    assertThat(mismatch.get().delta()).isEqualByComparingTo("5000.00");
  }

  @Test
  void validateCumulative_excludesReimportedRowByBrokerRef() {
    // The row is a re-import of an already-stored execution (same Our ref); its stored copy must be
    // excluded from the running total. With exclusion cumulative=100000 (on target → empty);
    // without it cumulative=200000 would be a false over-fill.
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    var existing = List.of(execAmount("DLA-DUP", new BigDecimal("100000.00")));
    SebPendingTransactionRow row = fundBuyRowWithRef("DLA-DUP", new BigDecimal("100000.00"));

    assertThat(validator.validateCumulative(order, row, existing, PROPERTIES)).isEmpty();
  }

  @Test
  void validateCumulative_nullTarget_returnsEmpty() {
    TransactionOrder order = fundBuyOrder(null);
    SebPendingTransactionRow row = fundBuyRowWithRef("REF2", new BigDecimal("5000.00"));

    assertThat(validator.validateCumulative(order, row, List.of(), PROPERTIES)).isEmpty();
  }

  @Test
  void validateCumulative_etfQuantityOverFill_returnsMismatch() {
    // cumulative 101 vs target 100 = +1 (> 0.0001 quantity tolerance)
    TransactionOrder order = etfBuyOrder(new BigDecimal("100"));
    var existing = List.of(execQuantity("REF1", new BigDecimal("100")));
    SebPendingTransactionRow row = etfRowWithRef("REF2", new BigDecimal("1"));

    Optional<QuantityAmountMismatchEvent> mismatch =
        validator.validateCumulative(order, row, existing, PROPERTIES);

    assertThat(mismatch).isPresent();
    assertThat(mismatch.get().actual()).isEqualByComparingTo("101");
  }

  @Test
  void validateCumulative_fundBuyZeroTarget_returnsEmpty() {
    // zero ordered amount → relativeExcess short-circuits to zero, so never flagged an over-fill
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));
    SebPendingTransactionRow row = fundBuyRowWithRef("REF2", new BigDecimal("5000.00"));

    assertThat(validator.validateCumulative(order, row, List.of(), PROPERTIES)).isEmpty();
  }

  @Test
  void isShortFill_fundBuyExecutionsShortOfTarget_returnsTrue() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    var executions = List.of(execAmount("REF1", new BigDecimal("50000.00")));

    assertThat(validator.isShortFill(order, executions, PROPERTIES)).isTrue();
  }

  @Test
  void isShortFill_fundBuyExecutionsOnTarget_returnsFalse() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("100000.00"));
    var executions = List.of(execAmount("REF1", new BigDecimal("100000.00")));

    assertThat(validator.isShortFill(order, executions, PROPERTIES)).isFalse();
  }

  @Test
  void isShortFill_nullTarget_returnsFalse() {
    TransactionOrder order = fundBuyOrder(null);
    var executions = List.of(execAmount("REF1", new BigDecimal("50000.00")));

    assertThat(validator.isShortFill(order, executions, PROPERTIES)).isFalse();
  }

  @Test
  void isShortFill_zeroTargetFundBuy_returnsFalse() {
    TransactionOrder order = fundBuyOrder(new BigDecimal("0.00"));

    assertThat(validator.isShortFill(order, List.of(), PROPERTIES)).isFalse();
  }

  @Test
  void isShortFill_etfQuantityShort_returnsTrue() {
    TransactionOrder order = etfBuyOrder(new BigDecimal("100"));
    var executions = List.of(execQuantity("REF1", new BigDecimal("60")));

    assertThat(validator.isShortFill(order, executions, PROPERTIES)).isTrue();
  }

  private static TransactionExecution execAmount(String brokerRef, BigDecimal totalConsideration) {
    return TransactionExecution.builder()
        .brokerTransactionId(brokerRef)
        .totalConsideration(totalConsideration)
        .source("SEB")
        .build();
  }

  private static TransactionExecution execQuantity(String brokerRef, BigDecimal executedQuantity) {
    return TransactionExecution.builder()
        .brokerTransactionId(brokerRef)
        .executedQuantity(executedQuantity)
        .source("SEB")
        .build();
  }

  private static TransactionOrder etfBuyOrder(BigDecimal orderQuantity) {
    return TransactionOrder.builder()
        .id(1L)
        .fund(TKF100)
        .instrumentIsin("IE00BFG1TM61")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(orderQuantity)
        .orderVenue(SEB)
        .orderStatus(SENT)
        .build();
  }

  private static SebPendingTransactionRow fundBuyRowWithRef(String ourRef, BigDecimal total) {
    return new SebPendingTransactionRow(
        null,
        ourRef,
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

  private static SebPendingTransactionRow etfRowWithRef(String ourRef, BigDecimal quantity) {
    return new SebPendingTransactionRow(
        null,
        ourRef,
        "IE00BFG1TM61",
        quantity,
        null,
        null,
        null,
        null,
        BUY,
        null,
        null,
        "Tuleva Täiendav Kogumisfond",
        null,
        null);
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
