package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Safety net for the FundTicker cutover: benchmark legs resolved from the enum must match the ones
// resolved from instrument_reference, for every tracked ISIN. Dies with the enum.
@SpringBootTest
@ActiveProfiles("test")
class BenchmarkLegParityTest {

  @Autowired private BenchmarkLegResolver benchmarkLegResolver;

  @Autowired private InstrumentReferenceService instrumentReferenceService;

  @Test
  void everyIsinResolvesToTheSameBenchmarkLegThroughTheTableAsThroughTheEnum() {
    for (var ticker : FundTicker.values()) {
      var isin = ticker.getIsin();
      var instrument = instrument(isin);
      var leg = benchmarkLegResolver.resolve(isin);
      var proxy =
          instrumentReferenceService.resolveBenchmarkProxy(
              instrument.getBenchmarkCategory(), instrument.isExchangeTraded());

      assertThat(proxy.isPresent())
          .as("benchmark leg presence: isin=%s", isin)
          .isEqualTo(leg.isPresent());

      if (leg.isEmpty()) {
        continue;
      }

      assertThat(proxy.get().storageKey())
          .as("benchmark series key: isin=%s", isin)
          .isEqualTo(leg.get().seriesKey());
      assertThat(proxy.get().isIndex())
          .as("benchmark leg is an index series: isin=%s", isin)
          .isEqualTo(leg.get().isIndex());

      if (!leg.get().isIndex()) {
        assertThat(proxy.get().proxyInstrument().getIsin())
            .as("benchmark proxy ETF: isin=%s", isin)
            .isEqualTo(leg.get().proxyEtf().getIsin());
      }
    }
  }

  private InstrumentReference instrument(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .orElseThrow(() -> new AssertionError("Missing row in instrument_reference: isin=" + isin));
  }
}
