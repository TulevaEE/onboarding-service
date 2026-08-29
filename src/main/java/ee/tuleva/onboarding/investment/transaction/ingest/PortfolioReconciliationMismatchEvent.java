package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record PortfolioReconciliationMismatchEvent(
    TulevaFund fund, LocalDate asOfDate, List<MismatchEntry> mismatches) {

  public PortfolioReconciliationMismatchEvent {
    mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
  }

  public record MismatchEntry(
      String isin,
      @Nullable BigDecimal ourQuantity,
      @Nullable BigDecimal theirQuantity,
      BigDecimal delta) {}
}
