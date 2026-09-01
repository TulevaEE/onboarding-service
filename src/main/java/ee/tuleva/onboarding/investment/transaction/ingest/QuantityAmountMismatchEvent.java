package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record QuantityAmountMismatchEvent(
    SebPendingTransactionRow row,
    TransactionOrder order,
    MismatchKind kind,
    @Nullable BigDecimal expected,
    @Nullable BigDecimal actual,
    @Nullable BigDecimal delta,
    BigDecimal tolerance,
    BigDecimal nearMissMultiplier,
    @Nullable LocalDate reportDate) {

  enum MismatchKind {
    ETF_QUANTITY,
    FUND_BUY_AMOUNT,
    FUND_SELL_QUANTITY
  }

  QuantityAmountMismatchEvent withReportDate(LocalDate newReportDate) {
    return new QuantityAmountMismatchEvent(
        row, order, kind, expected, actual, delta, tolerance, nearMissMultiplier, newReportDate);
  }
}
