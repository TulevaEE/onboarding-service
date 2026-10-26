package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BenchmarkLegResolver {

  private final InstrumentReferenceService instrumentReferenceService;

  Optional<BenchmarkProxy> resolve(String isin, LocalDate date) {
    return instrumentReferenceService.resolveBenchmarkProxy(isin, date);
  }
}
