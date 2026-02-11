package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NavCalculationResultTest {

  @Test
  void totalAssets_sumsAllAssetComponents() {
    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .securitiesValue(new BigDecimal("1000000.00"))
            .cashPosition(new BigDecimal("50000.00"))
            .receivables(new BigDecimal("10000.00"))
            .pendingSubscriptions(new BigDecimal("25000.00"))
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(ZERO)
            .depotFeeAccrual(ZERO)
            .payables(ZERO)
            .blackrockAdjustment(new BigDecimal("500.00"))
            .aum(new BigDecimal("1085500.00"))
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("10.85500"))
            .calculatedAt(Instant.now())
            .componentDetails(Map.of())
            .build();

    assertThat(result.totalAssets()).isEqualByComparingTo("1085500.00");
  }

  @Test
  void totalAssets_excludesNegativeBlackrockAdjustment() {
    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .securitiesValue(new BigDecimal("1000000.00"))
            .cashPosition(new BigDecimal("50000.00"))
            .receivables(ZERO)
            .pendingSubscriptions(ZERO)
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(ZERO)
            .depotFeeAccrual(ZERO)
            .payables(ZERO)
            .blackrockAdjustment(new BigDecimal("-300.00"))
            .aum(new BigDecimal("1049700.00"))
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("10.49700"))
            .calculatedAt(Instant.now())
            .componentDetails(Map.of())
            .build();

    assertThat(result.totalAssets()).isEqualByComparingTo("1050000.00");
  }

  @Test
  void totalLiabilities_sumsAllLiabilityComponents() {
    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .securitiesValue(new BigDecimal("1000000.00"))
            .cashPosition(ZERO)
            .receivables(ZERO)
            .pendingSubscriptions(ZERO)
            .pendingRedemptions(new BigDecimal("15000.00"))
            .managementFeeAccrual(new BigDecimal("52.08"))
            .depotFeeAccrual(new BigDecimal("6.85"))
            .payables(new BigDecimal("5000.00"))
            .blackrockAdjustment(ZERO)
            .aum(new BigDecimal("979941.07"))
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("9.79941"))
            .calculatedAt(Instant.now())
            .componentDetails(Map.of())
            .build();

    assertThat(result.totalLiabilities()).isEqualByComparingTo("20058.93");
  }

  @Test
  void totalLiabilities_includesNegativeBlackrockAdjustmentAbs() {
    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .securitiesValue(new BigDecimal("1000000.00"))
            .cashPosition(ZERO)
            .receivables(ZERO)
            .pendingSubscriptions(ZERO)
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(ZERO)
            .depotFeeAccrual(ZERO)
            .payables(ZERO)
            .blackrockAdjustment(new BigDecimal("-300.00"))
            .aum(new BigDecimal("999700.00"))
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("9.99700"))
            .calculatedAt(Instant.now())
            .componentDetails(Map.of())
            .build();

    assertThat(result.totalLiabilities()).isEqualByComparingTo("300.00");
  }
}
