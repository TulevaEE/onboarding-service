package ee.tuleva.onboarding.instrument;

import java.time.LocalDate;
import java.util.List;

public sealed interface InstrumentDataFinding {

  List<String> isins();

  String describe();

  record AmbiguousLookupKey(String lookup, String key, List<String> isins)
      implements InstrumentDataFinding {

    public AmbiguousLookupKey {
      isins = List.copyOf(isins);
    }

    @Override
    public String describe() {
      return "Ambiguous %s, resolving to the first row: key=%s, isins=%s"
          .formatted(lookup, key, isins);
    }
  }

  record InactiveBenchmarkProxy(String benchmarkCategory, String role, String isin)
      implements InstrumentDataFinding {

    @Override
    public List<String> isins() {
      return List.of(isin);
    }

    @Override
    public String describe() {
      return ("Benchmark proxy is active = false, so its price series is no longer updated and the"
              + " benchmark is frozen: benchmarkCategory=%s, role=%s, proxyIsin=%s")
          .formatted(benchmarkCategory, role, isin);
    }
  }

  record PastBenchmarkProxyWithoutPrices(String holdingIsin, String proxyIsin, LocalDate validUntil)
      implements InstrumentDataFinding {

    @Override
    public List<String> isins() {
      return List.of(holdingIsin, proxyIsin);
    }

    @Override
    public String describe() {
      return ("Past benchmark proxy is listed on neither Xetra nor Euronext Paris, so a check of a"
              + " date in its period fails: holdingIsin=%s, proxyIsin=%s, validUntil=%s")
          .formatted(holdingIsin, proxyIsin, validUntil);
    }
  }

  record EodhdListedWithoutTicker(String isin) implements InstrumentDataFinding {

    @Override
    public List<String> isins() {
      return List.of(isin);
    }

    @Override
    public String describe() {
      return "Listed on EODHD but has no EODHD ticker, excluded from EODHD price fetching: isin=%s"
          .formatted(isin);
    }
  }
}
