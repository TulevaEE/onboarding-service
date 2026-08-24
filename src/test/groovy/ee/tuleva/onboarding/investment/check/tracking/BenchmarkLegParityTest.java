package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_CORE_MSCI_WORLD;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_EURO_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_GLOBAL_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_MSCI_EM;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Safety net for the FundTicker cutover: benchmark legs resolved from instrument_reference must
// match the ones the enum switch used to produce, for every tracked ISIN. Dies with the enum.
@SpringBootTest
@ActiveProfiles("test")
class BenchmarkLegParityTest {

  @Autowired private BenchmarkLegResolver benchmarkLegResolver;

  @Test
  void everyIsinResolvesToTheSameBenchmarkLegThroughTheTableAsThroughTheEnum() {
    for (var ticker : FundTicker.values()) {
      var isin = ticker.getIsin();
      var expected = legFromEnum(ticker);
      var actual = benchmarkLegResolver.resolve(isin);

      assertThat(actual.isPresent())
          .as("benchmark leg presence: isin=%s", isin)
          .isEqualTo(expected.isPresent());

      if (expected.isEmpty()) {
        continue;
      }

      assertThat(actual.get().storageKey())
          .as("benchmark series key: isin=%s", isin)
          .isEqualTo(expected.get().seriesKey());
      assertThat(actual.get().isIndex())
          .as("benchmark leg is an index series: isin=%s", isin)
          .isEqualTo(expected.get().isIndex());

      if (!expected.get().isIndex()) {
        assertThat(actual.get().proxyInstrument().getIsin())
            .as("benchmark proxy ETF: isin=%s", isin)
            .isEqualTo(expected.get().proxyEtf().getIsin());
      }
    }
  }

  // The enum-era BenchmarkLegResolver, verbatim, as the expectation to hold the new one against.
  private record EnumLeg(String seriesKey, FundTicker proxyEtf) {
    boolean isIndex() {
      return proxyEtf == null;
    }
  }

  private static Optional<EnumLeg> legFromEnum(FundTicker ticker) {
    var category = ticker.getBenchmarkCategory();
    if (category == null) {
      return Optional.empty();
    }

    var isEtf =
        ticker.getEodhdTicker().endsWith(".XETRA") || ticker.getEodhdTicker().endsWith(".PA.EODHD");

    return Optional.of(
        switch (category) {
          case EQUITY_DM ->
              isEtf ? proxy(ISHARES_CORE_MSCI_WORLD) : new EnumLeg("MSCI_WORLD", null);
          case EQUITY_EM -> isEtf ? proxy(ISHARES_MSCI_EM) : new EnumLeg("MSCI_EM", null);
          case BOND_EURO -> proxy(ISHARES_EURO_AGG_BOND_ETF);
          case BOND_GLOBAL -> proxy(ISHARES_GLOBAL_AGG_BOND_ETF);
        });
  }

  private static EnumLeg proxy(FundTicker proxyEtf) {
    return new EnumLeg(proxyEtf.getXetraStorageKey().orElseThrow(), proxyEtf);
  }
}
