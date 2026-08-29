package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD;

import ee.tuleva.onboarding.investment.InvestmentParameters;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvestmentParameterValues implements InvestmentParameters {

  private final InvestmentParameterRepository repository;

  @Override
  public BigDecimal navImpactVolumeThreshold(LocalDate asOf) {
    return repository.findLatestValue(NAV_IMPACT_VOLUME_THRESHOLD, asOf);
  }
}
