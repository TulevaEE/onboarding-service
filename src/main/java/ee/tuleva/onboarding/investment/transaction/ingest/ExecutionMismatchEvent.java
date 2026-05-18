package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;

record ExecutionMismatchEvent(
    Long executionId,
    String isin,
    BigDecimal execPrice,
    BigDecimal navPrice,
    BigDecimal deltaPercent,
    LocalDate tradeDate) {}
