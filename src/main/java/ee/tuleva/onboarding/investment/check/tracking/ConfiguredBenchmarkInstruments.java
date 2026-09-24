package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.instrument.BenchmarkInstruments;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ConfiguredBenchmarkInstruments implements BenchmarkInstruments {

  @Override
  public Set<String> benchmarkIsins() {
    return BenchmarkCheckBuilder.benchmarkIsins();
  }
}
