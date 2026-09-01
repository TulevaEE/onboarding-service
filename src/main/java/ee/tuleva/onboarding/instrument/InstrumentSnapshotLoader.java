package ee.tuleva.onboarding.instrument;

import ee.tuleva.onboarding.instrument.InstrumentDataFinding.AmbiguousLookupKey;
import ee.tuleva.onboarding.instrument.InstrumentDataFinding.EodhdListedWithoutTicker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InstrumentSnapshotLoader {

  private final InstrumentReferenceRepository instrumentReferenceRepository;
  private final BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository;

  Snapshot loadSnapshot() {
    var orderedInstruments = instrumentReferenceRepository.findAllByOrderByIdAsc();
    var proxies = benchmarkCategoryProxyRepository.findAll();

    var findings = new ArrayList<InstrumentDataFinding>();
    var newByIsin = new HashMap<String, InstrumentReference>();
    var newByBloomberg = new HashMap<String, InstrumentReference>();
    var newByEodhdTicker = new HashMap<String, InstrumentReference>();
    var newByShortTicker = new HashMap<String, InstrumentReference>();

    for (var instrument : orderedInstruments) {
      putFirstWins(newByIsin, instrument.getIsin(), instrument, "isin", findings);

      if (instrument.getBloombergTicker() != null) {
        putFirstWins(
            newByBloomberg,
            instrument.getBloombergTicker(),
            instrument,
            "bloombergTicker",
            findings);
      }

      if (instrument.getEodhdTicker() != null) {
        putFirstWins(
            newByEodhdTicker, instrument.getEodhdTicker(), instrument, "eodhdTicker", findings);
      } else if (instrument.isListedOnEodhd()) {
        findings.add(new EodhdListedWithoutTicker(instrument.getIsin()));
      }

      if (instrument.getYahooTicker() != null) {
        putFirstWins(
            newByShortTicker,
            extractShortTicker(instrument.getYahooTicker()),
            instrument,
            "shortTicker",
            findings);
      }
    }

    var newProxyByCategory = new HashMap<String, BenchmarkCategoryProxy>();
    for (var proxy : proxies) {
      var existing = newProxyByCategory.putIfAbsent(proxy.benchmarkCategory(), proxy);
      if (existing != null) {
        findings.add(
            new AmbiguousLookupKey(
                "benchmarkCategory",
                proxy.benchmarkCategory(),
                List.of(existing.etfProxyIsin(), proxy.etfProxyIsin())));
      }
    }

    return new Snapshot(
        List.copyOf(orderedInstruments),
        Map.copyOf(newByIsin),
        Map.copyOf(newByBloomberg),
        Map.copyOf(newByEodhdTicker),
        Map.copyOf(newByShortTicker),
        Map.copyOf(newProxyByCategory),
        List.copyOf(findings));
  }

  record Snapshot(
      List<InstrumentReference> instruments,
      Map<String, InstrumentReference> byIsin,
      Map<String, InstrumentReference> byBloombergTicker,
      Map<String, InstrumentReference> byEodhdTicker,
      Map<String, InstrumentReference> byShortTicker,
      Map<String, BenchmarkCategoryProxy> proxyByCategory,
      List<InstrumentDataFinding> findings) {}

  private static void putFirstWins(
      Map<String, InstrumentReference> map,
      String key,
      InstrumentReference instrument,
      String lookup,
      List<InstrumentDataFinding> findings) {
    var existing = map.putIfAbsent(key, instrument);
    if (existing != null) {
      findings.add(
          new AmbiguousLookupKey(lookup, key, List.of(existing.getIsin(), instrument.getIsin())));
    }
  }

  private static String extractShortTicker(String yahooTicker) {
    int dotIndex = yahooTicker.indexOf('.');
    return dotIndex > 0 ? yahooTicker.substring(0, dotIndex) : yahooTicker;
  }
}
