package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.LocalDate;

record QuantityAmountMismatchEvent(
    SebPendingTransactionRow row,
    TransactionOrder order,
    MismatchKind kind,
    BigDecimal expected,
    BigDecimal actual,
    BigDecimal delta,
    LocalDate reportDate) {

  enum MismatchKind {
    ETF_QUANTITY,
    FUND_BUY_AMOUNT,
    FUND_SELL_QUANTITY
  }

  QuantityAmountMismatchEvent withReportDate(LocalDate newReportDate) {
    return new QuantityAmountMismatchEvent(
        row, order, kind, expected, actual, delta, newReportDate);
  }
}
