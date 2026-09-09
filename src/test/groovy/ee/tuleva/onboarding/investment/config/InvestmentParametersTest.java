package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_PAYOUT_WARNING_THRESHOLD;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.savings.RedemptionAlertThresholds;
import ee.tuleva.onboarding.savings.fund.nav.NavImpactThreshold;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentParametersTest {

  @Mock private InvestmentParameterRepository repository;
  @InjectMocks private InvestmentParameterValues parameters;

  @Test
  void resolvesTheNavImpactVolumeThreshold() {
    var asOf = LocalDate.parse("2026-08-30");
    given(repository.findLatestValue(InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD, asOf))
        .willReturn(new BigDecimal("100000"));

    NavImpactThreshold investmentParameters = parameters;

    assertThat(investmentParameters.navImpactVolumeThreshold(asOf))
        .isEqualByComparingTo(new BigDecimal("100000"));
  }

  @Test
  void resolvesTheTkf100RedemptionPayoutWarningThreshold() {
    var asOf = LocalDate.parse("2026-08-30");
    given(repository.findLatestValueIfPresent(REDEMPTION_PAYOUT_WARNING_THRESHOLD, TKF100, asOf))
        .willReturn(Optional.of(new BigDecimal("40000")));

    RedemptionAlertThresholds thresholds = parameters;

    assertThat(thresholds.redemptionPayoutWarningThreshold(TKF100, asOf))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("40000")));
  }

  @Test
  void resolvesTheTkf100RedemptionLiquidityWarningShareOfAum() {
    var asOf = LocalDate.parse("2026-08-30");
    given(
            repository.findLatestValueIfPresent(
                REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM, TKF100, asOf))
        .willReturn(Optional.of(new BigDecimal("0.01")));

    RedemptionAlertThresholds thresholds = parameters;

    assertThat(thresholds.redemptionLiquidityWarningShareOfAum(TKF100, asOf))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("0.01")));
  }

  @Test
  void returnsEmptyWhenTheRedemptionPayoutThresholdIsNotSeeded() {
    var asOf = LocalDate.parse("2026-08-30");
    given(repository.findLatestValueIfPresent(REDEMPTION_PAYOUT_WARNING_THRESHOLD, TKF100, asOf))
        .willReturn(Optional.empty());

    RedemptionAlertThresholds thresholds = parameters;

    assertThat(thresholds.redemptionPayoutWarningThreshold(TKF100, asOf)).isEmpty();
  }
}
