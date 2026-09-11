package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record PortfolioReconciliationSkippedEvent(
    TulevaFund fund,
    LocalDate asOfDate,
    Map<String, BigDecimal> ourQuantities,
    Map<String, BigDecimal> theirQuantities) {

  boolean isLedgerMissing() {
    return ourQuantities.isEmpty();
  }

  Map<String, BigDecimal> availableQuantities() {
    return isLedgerMissing() ? theirQuantities : ourQuantities;
  }
}
