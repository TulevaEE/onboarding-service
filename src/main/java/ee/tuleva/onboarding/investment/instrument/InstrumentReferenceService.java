package ee.tuleva.onboarding.investment.instrument;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentReferenceService {

  private final InstrumentReferenceRepository instrumentReferenceRepository;
  private final BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository;
  private final ApplicationEventPublisher eventPublisher;

  private volatile Map<String, InstrumentReference> byIsin = Map.of();
  private volatile Map<String, InstrumentReference> byBloombergTicker = Map.of();
  private volatile Map<String, InstrumentReference> byShortTicker = Map.of();
  private volatile Map<String, BenchmarkCategoryProxy> proxyByCategory = Map.of();

  @PostConstruct
  void init() {
    refresh();
  }

  @Scheduled(cron = "0 5 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "InstrumentReferenceService_refresh",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  void scheduledRefresh() {
    refresh();
  }

  private void refresh() {
    try {
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
          newByShortTicker.put(shortTicker, instrument);
        }
      }

      var newProxyByCategory = new HashMap<String, BenchmarkCategoryProxy>();
      for (var proxy : proxies) {
        newProxyByCategory.put(proxy.benchmarkCategory(), proxy);
      }

      byIsin = Map.copyOf(newByIsin);
      byBloombergTicker = Map.copyOf(newByBloomberg);
      byShortTicker = Map.copyOf(newByShortTicker);
      proxyByCategory = Map.copyOf(newProxyByCategory);

      log.info(
          "Instrument reference cache refreshed: instruments={}, proxies={}",
          instruments.size(),
          proxies.size());

      eventPublisher.publishEvent(new InstrumentCacheRefreshedEvent(instruments.size()));
    } catch (Exception e) {
      log.error("Failed to refresh instrument reference cache", e);
    }
  }

  // --- Lookup methods (mirrors FundTicker static methods) ---

  public Optional<InstrumentReference> findByIsin(String isin) {
    return Optional.ofNullable(byIsin.get(isin));
  }

  public Optional<InstrumentReference> findByTicker(String ticker) {
    return Optional.ofNullable(byShortTicker.get(ticker));
  }

  public Optional<InstrumentReference> findByBloombergTicker(String bloombergTicker) {
    return Optional.ofNullable(byBloombergTicker.get(bloombergTicker));
  }

  public List<InstrumentReference> findAll() {
    return List.copyOf(byIsin.values());
  }

  // --- Filtered lists (mirrors FundTicker.getXetraIsins() etc.) ---

  public List<String> getXetraIsins() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(i -> i.getEodhdTicker() != null && i.getEodhdTicker().endsWith(".XETRA"))
        .map(InstrumentReference::getIsin)
        .toList();
  }

  public List<String> getEuronextParisIsins() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(i -> i.getEodhdTicker() != null && i.getEodhdTicker().endsWith(".PA.EODHD"))
        .map(InstrumentReference::getIsin)
        .toList();
  }

  public List<String> getEodhdTickers() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(InstrumentReference::isListedOnEodhd)
        .map(InstrumentReference::getEodhdTicker)
        .toList();
  }

  public List<String> getYahooTickers() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(i -> i.getYahooTicker() != null)
        .map(InstrumentReference::getYahooTicker)
        .toList();
  }

  public List<InstrumentReference> getBlackrockFunds() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(i -> i.getBlackrockProductId() != null)
        .toList();
  }

  public List<InstrumentReference> getMorningstarFunds() {
    return byIsin.values().stream()
        .filter(InstrumentReference::isActive)
        .filter(i -> i.getMorningstarId() != null)
        .toList();
  }

  // --- Benchmark proxy resolution ---

  public Optional<String> resolveBenchmarkProxy(String benchmarkCategory, boolean exchangeTraded) {
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
        InstrumentReference::getXetraStorageKey,
        InstrumentReference::getEuronextParisStorageKey,
        i -> Optional.ofNullable(i.getEodhdTicker()),
        i -> Optional.ofNullable(i.getYahooTicker()));
  }

  private static String extractShortTicker(String yahooTicker) {
    int dotIndex = yahooTicker.indexOf('.');
    return dotIndex > 0 ? yahooTicker.substring(0, dotIndex) : yahooTicker;
  }
}
