package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
record TdAttributionResult(
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
    int businessDays,
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
