package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.*;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.BLACKROCK;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriorityPriceProvider {

  private static final int MAX_LOOKBACK_DAYS = 14;

  public record PriceFeed(
      PriceSource source, Function<InstrumentReference, Optional<String>> storageKey) {}

  private static final List<PriceFeed> PRICE_FEEDS =
      List.of(
          new PriceFeed(BLACKROCK, InstrumentReference::getBlackrockStorageKey),
          new PriceFeed(MORNINGSTAR, InstrumentReference::getMorningstarStorageKey),
          new PriceFeed(EODHD, InstrumentReference::getEodhdStorageKey),
          new PriceFeed(DEUTSCHE_BOERSE, InstrumentReference::getXetraStorageKey),
          new PriceFeed(EURONEXT, InstrumentReference::getEuronextParisStorageKey),
          new PriceFeed(YAHOO, instrument -> Optional.ofNullable(instrument.getYahooTicker())));

  public static List<PriceFeed> priceFeeds() {
    return PRICE_FEEDS;
  }

  private final FundValueProvider fundValueProvider;
  private final InstrumentReferenceService instrumentReferenceService;

  public Optional<FundValue> resolve(String isin, LocalDate date) {
    return resolve(isin, date, null);
  }

  public Optional<FundValue> resolve(String isin, LocalDate date, @Nullable Instant updatedBefore) {
    return instrumentReferenceService
        .findByIsin(isin)
        .flatMap(instrument -> resolveForInstrument(instrument, date, updatedBefore));
  }

  private Optional<FundValue> resolveForInstrument(
      InstrumentReference instrument, LocalDate date, @Nullable Instant updatedBefore) {
    LocalDate earliestAllowed = date.minusDays(MAX_LOOKBACK_DAYS);

    return PRICE_FEEDS.stream()
        .map(feed -> feed.storageKey().apply(instrument))
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
      String storageKey, LocalDate date, @Nullable Instant updatedBefore) {
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
