package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_CORE_MSCI_WORLD;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_EURO_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_GLOBAL_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_MSCI_EM;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// The BENCHMARK_MODEL check does not compare every holding against an index. A holding that is
// itself a mutual fund gets the index series; an ETF gets a proxy ETF, and a bond holding always
// does, whatever its own type. Which leg a holding got decides what its measured difference already
// contains: against an index it contains the holding's whole OCF, against a proxy only the part the
// proxy does not also pay. The attribution cannot split the measured ETF layer without knowing
// that, so the rule lives here rather than inside the one service that runs the comparison.
final class BenchmarkLegResolver {

  private BenchmarkLegResolver() {}

  // proxyEtf is null when the leg is an index series, which is the case where the comparison is
  // complete and the measured difference needs no restoration.
  record BenchmarkLeg(String seriesKey, @Nullable FundTicker proxyEtf) {
    boolean isIndex() {
      return proxyEtf == null;
    }
  }

  static Optional<BenchmarkLeg> resolve(String isin) {
    var ticker = FundTicker.findByIsin(isin).orElse(null);
    if (ticker == null) {
      return Optional.empty();
    }
    var category = ticker.getBenchmarkCategory();
    if (category == null) {
      return Optional.empty();
    }

    var isEtf =
        ticker.getEodhdTicker().endsWith(".XETRA") || ticker.getEodhdTicker().endsWith(".PA.EODHD");

    return Optional.of(
        switch (category) {
          case EQUITY_DM ->
              isEtf ? proxy(ISHARES_CORE_MSCI_WORLD) : new BenchmarkLeg("MSCI_WORLD", null);
          case EQUITY_EM -> isEtf ? proxy(ISHARES_MSCI_EM) : new BenchmarkLeg("MSCI_EM", null);
          case BOND_EURO -> proxy(ISHARES_EURO_AGG_BOND_ETF);
          case BOND_GLOBAL -> proxy(ISHARES_GLOBAL_AGG_BOND_ETF);
        });
  }

  private static BenchmarkLeg proxy(FundTicker proxyEtf) {
    return new BenchmarkLeg(proxyEtf.getXetraStorageKey().orElseThrow(), proxyEtf);
  }
}
