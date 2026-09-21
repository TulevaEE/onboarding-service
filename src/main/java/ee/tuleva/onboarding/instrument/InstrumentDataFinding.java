package ee.tuleva.onboarding.instrument;

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
