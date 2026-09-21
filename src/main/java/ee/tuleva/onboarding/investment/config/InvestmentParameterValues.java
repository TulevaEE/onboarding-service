package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_PAYOUT_WARNING_THRESHOLD;

import ee.tuleva.onboarding.savings.RedemptionAlertThresholds;
import ee.tuleva.onboarding.savings.fund.nav.NavImpactThreshold;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvestmentParameterValues implements NavImpactThreshold, RedemptionAlertThresholds {

  private final InvestmentParameterRepository repository;

  @Override
  public BigDecimal navImpactVolumeThreshold(LocalDate asOf) {
    return repository.findLatestValue(NAV_IMPACT_VOLUME_THRESHOLD, asOf);
  }

  @Override
  public Optional<BigDecimal> redemptionPayoutWarningThreshold(TulevaFund fund, LocalDate asOf) {
    return repository.findLatestValueIfPresent(REDEMPTION_PAYOUT_WARNING_THRESHOLD, fund, asOf);
  }

  @Override
  public Optional<BigDecimal> redemptionLiquidityWarningShareOfAum(
      TulevaFund fund, LocalDate asOf) {
    return repository.findLatestValueIfPresent(
        REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM, fund, asOf);
  }
}
