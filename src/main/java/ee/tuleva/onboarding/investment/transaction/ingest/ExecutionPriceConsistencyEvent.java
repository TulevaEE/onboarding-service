package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;

record ExecutionPriceConsistencyEvent(
    Long orderId,
    String isin,
    BigDecimal minUnitPrice,
    BigDecimal maxUnitPrice,
    BigDecimal relativeSpread,
    BigDecimal tolerance,
    LocalDate reportDate) {

  ExecutionPriceConsistencyEvent withReportDate(LocalDate newReportDate) {
    return new ExecutionPriceConsistencyEvent(
        orderId, isin, minUnitPrice, maxUnitPrice, relativeSpread, tolerance, newReportDate);
  }
}
