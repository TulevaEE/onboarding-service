package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.*;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.BLACKROCK;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriorityPriceProvider {

  private static final int MAX_LOOKBACK_DAYS = 14;

  public record PriceFeed(PriceSource source, Function<FundTicker, Optional<String>> storageKey) {}

  private static final List<PriceFeed> PRICE_FEEDS =
      List.of(
          new PriceFeed(BLACKROCK, FundTicker::getBlackrockStorageKey),
          new PriceFeed(MORNINGSTAR, FundTicker::getMorningstarStorageKey),
          new PriceFeed(EODHD, ticker -> Optional.of(ticker.getEodhdTicker())),
          new PriceFeed(DEUTSCHE_BOERSE, FundTicker::getXetraStorageKey),
          new PriceFeed(EURONEXT, FundTicker::getEuronextParisStorageKey),
          new PriceFeed(YAHOO, ticker -> Optional.of(ticker.getYahooTicker())));

  public static List<PriceFeed> priceFeeds() {
    return PRICE_FEEDS;
  }

  private final FundValueProvider fundValueProvider;

  public Optional<FundValue> resolve(String isin, LocalDate date) {
    return resolve(isin, date, null);
  }

  public Optional<FundValue> resolve(String isin, LocalDate date, Instant updatedBefore) {
    return FundTicker.findByIsin(isin)
        .flatMap(ticker -> resolveForTicker(ticker, date, updatedBefore));
  }

  private Optional<FundValue> resolveForTicker(
      FundTicker ticker, LocalDate date, Instant updatedBefore) {
    LocalDate earliestAllowed = date.minusDays(MAX_LOOKBACK_DAYS);

    return PRICE_FEEDS.stream()
        .map(feed -> feed.storageKey().apply(ticker))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(storageKey -> fetchLatestValue(storageKey, date, updatedBefore))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(fundValue -> fundValue.value().compareTo(ZERO) != 0)
        .filter(fundValue -> !fundValue.date().isBefore(earliestAllowed))
        .max(
            Comparator.comparing(FundValue::date)
                .thenComparing(fundValue -> providerPriority(fundValue.provider())));
  }

  private Optional<FundValue> fetchLatestValue(
      String storageKey, LocalDate date, Instant updatedBefore) {
    if (updatedBefore != null) {
      return fundValueProvider.getLatestValue(storageKey, date, updatedBefore);
    }
    return fundValueProvider.getLatestValue(storageKey, date);
  }

  private int providerPriority(String provider) {
    List<String> providerNames = PRICE_FEEDS.stream().map(feed -> feed.source().name()).toList();
    int index = providerNames.indexOf(provider);
    return index >= 0 ? providerNames.size() - index : -1;
  }
}
