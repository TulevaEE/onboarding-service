package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TransactionDailySummary(LocalDate date, List<FundSummary> funds) {

  public record FundSummary(
      TulevaFund fund,
      int unsettledOrderCount,
      BigDecimal unsettledOrderAmount,
      @Nullable Long latestBatchId,
      @Nullable BatchStatus latestBatchStatus,
      @Nullable Instant latestBatchCreatedAt) {}
}
