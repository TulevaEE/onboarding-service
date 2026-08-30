package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD;

import ee.tuleva.onboarding.savings.fund.nav.NavImpactThreshold;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvestmentParameterValues implements NavImpactThreshold {

  private final InvestmentParameterRepository repository;

  @Override
  public BigDecimal navImpactVolumeThreshold(LocalDate asOf) {
    return repository.findLatestValue(NAV_IMPACT_VOLUME_THRESHOLD, asOf);
  }
}
