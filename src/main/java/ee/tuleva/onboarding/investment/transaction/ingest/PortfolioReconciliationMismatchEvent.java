package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioReconciliationMismatchEvent(
    TulevaFund fund, LocalDate asOfDate, List<MismatchEntry> mismatches) {

  public PortfolioReconciliationMismatchEvent {
    mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
  }

  public record MismatchEntry(
      String isin, BigDecimal ourQuantity, BigDecimal theirQuantity, BigDecimal delta) {}
}
