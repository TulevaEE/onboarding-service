package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record TdAttributionResult(
    TulevaFund fund,
    LocalDate periodStart,
    LocalDate periodEnd,
    PeriodType periodType,
    BigDecimal fundReturn,
    BigDecimal modelReturn,
    BigDecimal tdGeometric,
    BigDecimal scalingFactor,
    BigDecimal mgmtFeeDrag,
    BigDecimal depotFeeDrag,
    BigDecimal cashDrag,
    BigDecimal nonSecurityDrag,
    BigDecimal weightDeviation,
    BigDecimal transactionCosts,
    BigDecimal residual,
    BigDecimal etfOcfDrag,
    BigDecimal etfTrackingResidual,
    BigDecimal tdVsBenchmark,
    // Count of daily NAV events in the period (not the working-day count — a missing working day
    // both lowers this and shows up in checks.seriesGapDays).
    int navEventCount,
    BigDecimal avgAum,
    BigDecimal avgCashPct,
    List<InstrumentAttribution> instrumentDetails,
    Map<String, Object> checks) {

  @Builder
  record InstrumentAttribution(
      String isin,
      String instrumentName,
      BigDecimal modelWeight,
      BigDecimal avgActualWeight,
      BigDecimal weightDevContribution,
      BigDecimal securityReturn) {}
}
