package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_CORE_MSCI_WORLD;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_EURO_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_GLOBAL_AGG_BOND_ETF;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.ISHARES_MSCI_EM;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class BenchmarkLegResolver {

  private BenchmarkLegResolver() {}

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
