package ee.tuleva.onboarding.instrument;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private volatile Map<String, InstrumentReference> byShortTicker = Map.of();
  private volatile Map<String, BenchmarkCategoryProxy> proxyByCategory = Map.of();
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
      log.error("Failed to refresh instrument reference cache", e);
    }
  }

  public Instant getLastRefreshedAt() {
    var refreshedAt = lastRefreshedAt;
    if (refreshedAt == null) {
      throw new IllegalStateException("Instrument reference cache has never been loaded");
    }
    return refreshedAt;
  }

  private void apply(Snapshot snapshot) {
    byIsin = snapshot.byIsin();
    byBloombergTicker = snapshot.byBloombergTicker();
    byShortTicker = snapshot.byShortTicker();
    proxyByCategory = snapshot.proxyByCategory();
    lastRefreshedAt = clock.instant();

    log.info(
        "Instrument reference cache refreshed: instruments={}, proxies={}",
        byIsin.size(),
        proxyByCategory.size());
  }

  private Snapshot readSnapshot() {
    var instruments = instrumentReferenceRepository.findAll();
    var proxies = benchmarkCategoryProxyRepository.findAll();

    var newByIsin = new HashMap<String, InstrumentReference>();
    var newByBloomberg = new HashMap<String, InstrumentReference>();
    var newByShortTicker = new HashMap<String, InstrumentReference>();

    for (var instrument : instruments) {
      newByIsin.put(instrument.getIsin(), instrument);

      if (instrument.getBloombergTicker() != null) {
        newByBloomberg.put(instrument.getBloombergTicker(), instrument);
      }

      if (instrument.getYahooTicker() != null) {
        String shortTicker = extractShortTicker(instrument.getYahooTicker());
        var shadowed = newByShortTicker.put(shortTicker, instrument);
        if (shadowed != null) {
          log.warn(
              "Short-ticker collision in instrument cache: shortTicker={}, isins=[{}, {}] — findByTicker resolves only the last",
              shortTicker,
              shadowed.getIsin(),
              instrument.getIsin());
        }
      }
    }

    var newProxyByCategory = new HashMap<String, BenchmarkCategoryProxy>();
    for (var proxy : proxies) {
      newProxyByCategory.put(proxy.benchmarkCategory(), proxy);
    }

    return new Snapshot(
        Map.copyOf(newByIsin),
        Map.copyOf(newByBloomberg),
        Map.copyOf(newByShortTicker),
        Map.copyOf(newProxyByCategory));
  }

  private record Snapshot(
      Map<String, InstrumentReference> byIsin,
      Map<String, InstrumentReference> byBloombergTicker,
      Map<String, InstrumentReference> byShortTicker,
      Map<String, BenchmarkCategoryProxy> proxyByCategory) {}

  // --- Lookup methods (mirrors FundTicker static methods) ---

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

  public List<InstrumentReference> activeInstruments() {
    return byIsin.values().stream().filter(InstrumentReference::isActive).toList();
  }

  // --- Filtered lists (mirrors FundTicker.getXetraIsins() etc.) ---

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

  // --- Benchmark proxy resolution ---

  public Optional<String> resolveBenchmarkProxy(
      @Nullable String benchmarkCategory, boolean exchangeTraded) {
    if (benchmarkCategory == null) {
      return Optional.empty();
    }
    var proxy = proxyByCategory.get(benchmarkCategory);
    if (proxy == null) {
      return Optional.empty();
    }
    if (exchangeTraded) {
      return Optional.ofNullable(proxy.etfProxyStorageKey());
    }
    return Optional.ofNullable(proxy.indexProxyKey());
  }

  // --- Storage key helpers ---

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

  private static String extractShortTicker(String yahooTicker) {
    int dotIndex = yahooTicker.indexOf('.');
    return dotIndex > 0 ? yahooTicker.substring(0, dotIndex) : yahooTicker;
  }
}
