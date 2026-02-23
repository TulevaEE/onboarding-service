package ee.tuleva.onboarding.comparisons.fundvalue;

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

  private static final List<String> PROVIDER_PRIORITY =
      List.of("BLACKROCK", "MORNINGSTAR", "EODHD", "YAHOO");

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

    return storageKeyResolvers().stream()
        .map(resolver -> resolver.apply(ticker))
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
    int index = PROVIDER_PRIORITY.indexOf(provider);
    return index >= 0 ? PROVIDER_PRIORITY.size() - index : -1;
  }

  private List<Function<FundTicker, Optional<String>>> storageKeyResolvers() {
    return List.of(
        FundTicker::getBlackrockStorageKey,
        FundTicker::getMorningstarStorageKey,
        ticker -> Optional.of(ticker.getEodhdTicker()),
        ticker -> Optional.of(ticker.getYahooTicker()));
  }
}
