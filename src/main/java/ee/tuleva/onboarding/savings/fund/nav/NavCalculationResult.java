package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record NavCalculationResult(
    TulevaFund fund,
    LocalDate calculationDate,
    BigDecimal securitiesValue,
    BigDecimal cashPosition,
    BigDecimal receivables,
    BigDecimal pendingSubscriptions,
    BigDecimal pendingRedemptions,
    BigDecimal managementFeeAccrual,
    BigDecimal depotFeeAccrual,
    BigDecimal payables,
    BigDecimal blackrockAdjustment,
    BigDecimal aum,
    BigDecimal unitsOutstanding,
    BigDecimal navPerUnit,
    LocalDate positionReportDate,
    LocalDate priceDate,
    Instant calculatedAt,
    List<SecurityDetail> securitiesDetail) {

  public record SecurityDetail(
      String isin, String ticker, BigDecimal units, BigDecimal price, BigDecimal marketValue) {}

  public BigDecimal totalAssets() {
    return securitiesValue
        .add(cashPosition)
        .add(receivables)
        .add(pendingSubscriptions)
        .add(blackrockAdjustment.max(BigDecimal.ZERO));
  }

  public BigDecimal totalLiabilities() {
    return pendingRedemptions
        .add(managementFeeAccrual)
        .add(depotFeeAccrual)
        .add(payables)
        .add(blackrockAdjustment.min(BigDecimal.ZERO).negate());
  }
}
