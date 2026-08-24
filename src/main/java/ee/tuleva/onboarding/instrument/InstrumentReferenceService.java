package ee.tuleva.onboarding.instrument;

import ee.tuleva.onboarding.instrument.InstrumentDataFinding.AmbiguousLookupKey;
import ee.tuleva.onboarding.instrument.InstrumentDataFinding.EodhdListedWithoutTicker;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentReferenceService {

  private static final int MIN_ACCEPTABLE_ROW_COUNT_PERCENT = 80;

  private final InstrumentReferenceRepository instrumentReferenceRepository;
  private final BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository;
  private final Clock clock;

  private volatile Map<String, InstrumentReference> byIsin = Map.of();
  private volatile Map<String, InstrumentReference> byBloombergTicker = Map.of();
  private volatile Map<String, InstrumentReference> byEodhdTicker = Map.of();
  private volatile Map<String, InstrumentReference> byShortTicker = Map.of();
  private volatile Map<String, BenchmarkCategoryProxy> proxyByCategory = Map.of();
  private volatile List<InstrumentDataFinding> dataFindings = List.of();
  private volatile @Nullable Instant lastRefreshedAt;

  @PostConstruct
  void init() {
    Snapshot snapshot;
    try {
      snapshot = readSnapshot();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to load the instrument reference cache at startup", e);
    }

    if (snapshot.byIsin().isEmpty()) {
      throw new IllegalStateException("Instrument reference table holds no rows: instruments=0");
    }

    apply(snapshot);
  }

  @Scheduled(cron = "0 5 * * * *", zone = "Europe/Tallinn")
  void scheduledRefresh() {
    try {
      var snapshot = readSnapshot();
      var liveCount = byIsin.size();
      var loadedCount = snapshot.byIsin().size();

      if (loadedCount * 100 < liveCount * MIN_ACCEPTABLE_ROW_COUNT_PERCENT) {
        log.error(
            "Refusing instrument reference cache refresh, row count collapsed: liveInstruments={},"
                + " loadedInstruments={}, lastRefreshedAt={}",
            liveCount,
            loadedCount,
            lastRefreshedAt);
        return;
      }

      apply(snapshot);
    } catch (Exception e) {
      log.error(
          "Failed to refresh instrument reference cache, keeping the live snapshot:"
              + " liveInstruments={}, lastRefreshedAt={}",
          byIsin.size(),
          lastRefreshedAt,
          e);
    }
  }

  public Instant getLastRefreshedAt() {
    var refreshedAt = lastRefreshedAt;
    if (refreshedAt == null) {
      throw new IllegalStateException("Instrument reference cache has never been loaded");
    }
    return refreshedAt;
  }

  public List<InstrumentDataFinding> dataFindings() {
    return dataFindings;
  }

  private void apply(Snapshot snapshot) {
    byIsin = snapshot.byIsin();
    byBloombergTicker = snapshot.byBloombergTicker();
    byEodhdTicker = snapshot.byEodhdTicker();
    byShortTicker = snapshot.byShortTicker();
    proxyByCategory = snapshot.proxyByCategory();
    dataFindings = snapshot.findings();
    lastRefreshedAt = clock.instant();

    log.info(
        "Instrument reference cache refreshed: instruments={}, proxies={}, dataFindings={}",
        byIsin.size(),
        proxyByCategory.size(),
        dataFindings.size());

    if (!dataFindings.isEmpty()) {
      log.error(
          "Instrument reference data problems, cache applied anyway: dataFindings={}, details={}",
          dataFindings.size(),
          dataFindings.stream().map(InstrumentDataFinding::describe).toList());
    }
  }

  private Snapshot readSnapshot() {
    var instruments = instrumentReferenceRepository.findAllByOrderByIdAsc();
    var proxies = benchmarkCategoryProxyRepository.findAll();

    var findings = new ArrayList<InstrumentDataFinding>();
    var newByIsin = new HashMap<String, InstrumentReference>();
    var newByBloomberg = new HashMap<String, InstrumentReference>();
    var newByEodhdTicker = new HashMap<String, InstrumentReference>();
    var newByShortTicker = new HashMap<String, InstrumentReference>();

    for (var instrument : instruments) {
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
        Map.copyOf(newByIsin),
        Map.copyOf(newByBloomberg),
        Map.copyOf(newByEodhdTicker),
        Map.copyOf(newByShortTicker),
        Map.copyOf(newProxyByCategory),
        List.copyOf(findings));
  }

  private record Snapshot(
      Map<String, InstrumentReference> byIsin,
      Map<String, InstrumentReference> byBloombergTicker,
      Map<String, InstrumentReference> byEodhdTicker,
      Map<String, InstrumentReference> byShortTicker,
      Map<String, BenchmarkCategoryProxy> proxyByCategory,
      List<InstrumentDataFinding> findings) {}

  public Optional<InstrumentReference> findByIsin(String isin) {
    return Optional.ofNullable(byIsin.get(isin));
  }

  public Optional<SettlementTerms> settlementTerms(String isin) {
    return findByIsin(isin).flatMap(InstrumentReference::settlementTerms);
  }

  public Optional<InstrumentReference> findByTicker(String ticker) {
    return Optional.ofNullable(byShortTicker.get(ticker));
  }

  public Optional<InstrumentReference> findByBloombergTicker(String bloombergTicker) {
    return Optional.ofNullable(byBloombergTicker.get(bloombergTicker));
  }

  public Optional<InstrumentReference> findByEodhdTicker(String eodhdTicker) {
    return Optional.ofNullable(byEodhdTicker.get(eodhdTicker));
  }

  public List<InstrumentReference> activeInstruments() {
    return byIsin.values().stream().filter(InstrumentReference::isActive).toList();
  }

  public List<String> getXetraIsins() {
    return activeInstruments().stream()
        .filter(i -> i.getEodhdTicker() != null && i.getEodhdTicker().endsWith(".XETRA"))
        .map(InstrumentReference::getIsin)
        .toList();
  }

  public List<String> getEuronextParisIsins() {
    return activeInstruments().stream()
        .filter(i -> i.getEodhdTicker() != null && i.getEodhdTicker().endsWith(".PA.EODHD"))
        .map(InstrumentReference::getIsin)
        .toList();
  }

  public List<String> getEodhdTickers() {
    return activeInstruments().stream()
        .filter(InstrumentReference::isListedOnEodhd)
        .map(InstrumentReference::getEodhdTicker)
        .filter(Objects::nonNull)
        .toList();
  }

  public List<String> getYahooTickers() {
    return activeInstruments().stream()
        .filter(i -> i.getYahooTicker() != null)
        .map(InstrumentReference::getYahooTicker)
        .toList();
  }

  public List<InstrumentReference> getBlackrockFunds() {
    return activeInstruments().stream().filter(i -> i.getBlackrockProductId() != null).toList();
  }

  public List<InstrumentReference> getMorningstarFunds() {
    return activeInstruments().stream().filter(i -> i.getMorningstarId() != null).toList();
  }

  public Optional<BenchmarkProxy> resolveBenchmarkProxy(
      @Nullable String benchmarkCategory, boolean exchangeTraded) {
    if (benchmarkCategory == null) {
      return Optional.empty();
    }
    var proxy = proxyByCategory.get(benchmarkCategory);
    if (proxy == null) {
      throw new UnresolvableBenchmarkProxyException(
          "No benchmark proxy configured for benchmark category: benchmarkCategory=%s"
              .formatted(benchmarkCategory));
    }
    if (exchangeTraded) {
      return Optional.of(proxyInstrument(benchmarkCategory, "etfProxyIsin", proxy.etfProxyIsin()));
    }
    var indexSeriesKey = proxy.indexSeriesKey();
    if (indexSeriesKey != null) {
      return Optional.of(new BenchmarkProxy(null, indexSeriesKey));
    }
    var indexProxyIsin = proxy.indexProxyIsin();
    if (indexProxyIsin == null) {
      throw new UnresolvableBenchmarkProxyException(
          ("Benchmark proxy has neither an index proxy ISIN nor an index series key:"
                  + " benchmarkCategory=%s")
              .formatted(benchmarkCategory));
    }
    return Optional.of(proxyInstrument(benchmarkCategory, "indexProxyIsin", indexProxyIsin));
  }

  private BenchmarkProxy proxyInstrument(String benchmarkCategory, String role, String isin) {
    var instrument =
        findByIsin(isin)
            .orElseThrow(
                () ->
                    new UnresolvableBenchmarkProxyException(
                        ("Benchmark proxy instrument is missing from the instrument reference"
                                + " cache: benchmarkCategory=%s, role=%s, proxyIsin=%s")
                            .formatted(benchmarkCategory, role, isin)));

    if (instrument.getExchangeStorageKey().isEmpty()) {
      throw new UnresolvableBenchmarkProxyException(
          ("Benchmark proxy instrument is listed on neither Xetra nor Euronext Paris, so it has"
                  + " no price series: benchmarkCategory=%s, role=%s, proxyIsin=%s, eodhdTicker=%s")
              .formatted(benchmarkCategory, role, isin, instrument.getEodhdTicker()));
    }

    return new BenchmarkProxy(instrument, null);
  }

  public List<java.util.function.Function<InstrumentReference, Optional<String>>>
      storageKeyResolvers() {
    return List.of(
        InstrumentReference::getBlackrockStorageKey,
        InstrumentReference::getMorningstarStorageKey,
        i -> Optional.ofNullable(i.getEodhdTicker()),
        InstrumentReference::getXetraStorageKey,
        InstrumentReference::getEuronextParisStorageKey,
        i -> Optional.ofNullable(i.getYahooTicker()));
  }

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

  public static class UnresolvableBenchmarkProxyException extends IllegalStateException {
    public UnresolvableBenchmarkProxyException(String message) {
      super(message);
    }
  }
}
