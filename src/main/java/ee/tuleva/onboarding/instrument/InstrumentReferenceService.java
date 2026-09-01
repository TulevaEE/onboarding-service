package ee.tuleva.onboarding.instrument;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
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

  private final InstrumentSnapshotLoader snapshotLoader;
  private final Clock clock;

  private volatile List<InstrumentReference> instruments = List.of();
  private volatile Map<String, InstrumentReference> byIsin = Map.of();
  private volatile Map<String, InstrumentReference> byBloombergTicker = Map.of();
  private volatile Map<String, InstrumentReference> byEodhdTicker = Map.of();
  private volatile Map<String, InstrumentReference> byShortTicker = Map.of();
  private volatile Map<String, BenchmarkCategoryProxy> proxyByCategory = Map.of();
  private volatile List<InstrumentDataFinding> dataFindings = List.of();
  private volatile @Nullable Instant lastRefreshedAt;

  @PostConstruct
  void init() {
    InstrumentSnapshotLoader.Snapshot snapshot;
    try {
      snapshot = snapshotLoader.loadSnapshot();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to load the instrument reference cache at startup", e);
    }

    if (snapshot.instruments().isEmpty()) {
      throw new IllegalStateException("Instrument reference table holds no rows: instruments=0");
    }

    apply(snapshot);
  }

  @Scheduled(cron = "0 5 * * * *", zone = "Europe/Tallinn")
  void scheduledRefresh() {
    try {
      var snapshot = snapshotLoader.loadSnapshot();
      var liveCount = instruments.size();
      var loadedCount = snapshot.instruments().size();

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
          instruments.size(),
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

  private void apply(InstrumentSnapshotLoader.Snapshot snapshot) {
    instruments = snapshot.instruments();
    byIsin = snapshot.byIsin();
    byBloombergTicker = snapshot.byBloombergTicker();
    byEodhdTicker = snapshot.byEodhdTicker();
    byShortTicker = snapshot.byShortTicker();
    proxyByCategory = snapshot.proxyByCategory();
    dataFindings = snapshot.findings();
    lastRefreshedAt = clock.instant();

    log.info(
        "Instrument reference cache refreshed: instruments={}, proxies={}, dataFindings={}",
        instruments.size(),
        proxyByCategory.size(),
        dataFindings.size());

    if (!dataFindings.isEmpty()) {
      log.error(
          "Instrument reference data problems, cache applied anyway: dataFindings={}, details={}",
          dataFindings.size(),
          dataFindings.stream().map(InstrumentDataFinding::describe).toList());
    }
  }

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
    return instruments.stream().filter(InstrumentReference::isActive).toList();
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
        .map(InstrumentReference::getEodhdStorageKey)
        .flatMap(Optional::stream)
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
      return Optional.of(
          proxyInstrument(benchmarkCategory, ProxyRole.ETF_PROXY_ISIN, proxy.etfProxyIsin()));
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
    return Optional.of(
        proxyInstrument(benchmarkCategory, ProxyRole.INDEX_PROXY_ISIN, indexProxyIsin));
  }

  private BenchmarkProxy proxyInstrument(String benchmarkCategory, ProxyRole role, String isin) {
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

  private enum ProxyRole {
    ETF_PROXY_ISIN("etfProxyIsin"),
    INDEX_PROXY_ISIN("indexProxyIsin");

    private final String fieldName;

    ProxyRole(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public String toString() {
      return fieldName;
    }
  }

  public static class UnresolvableBenchmarkProxyException extends IllegalStateException {
    public UnresolvableBenchmarkProxyException(String message) {
      super(message);
    }
  }
}
