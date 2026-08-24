package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BenchmarkLegResolver {

  private final InstrumentReferenceService instrumentReferenceService;

  Optional<BenchmarkProxy> resolve(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .flatMap(
            instrument ->
                instrumentReferenceService.resolveBenchmarkProxy(
                    instrument.getBenchmarkCategory(), instrument.isExchangeTraded()));
  }
}
