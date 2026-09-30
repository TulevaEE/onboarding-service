package ee.tuleva.onboarding.investment.check.tracking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class PeriodicTdAttributionWriter {

  private final PeriodicTdAttributionRepository repository;

  @Transactional
  void replace(PeriodicTdAttribution attribution) {
    repository.deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
        attribution.getFund(),
        attribution.getPeriodStart(),
        attribution.getPeriodEnd(),
        attribution.getPeriodType());
    repository.save(attribution);
  }
}
