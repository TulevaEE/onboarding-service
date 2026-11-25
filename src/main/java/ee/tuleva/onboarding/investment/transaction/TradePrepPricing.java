package ee.tuleva.onboarding.investment.transaction;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
sealed interface TradePrepPricing {

  Map<String, TradePrepPrice> prices();

  LocalDate stalenessReference(LocalDate asOfDate);

  default Map<String, RevaluationPrice> revaluationPrices() {
    return prices().entrySet().stream()
        .flatMap(
            entry ->
                entry
                    .getValue()
                    .revaluation()
                    .map(price -> Map.entry(entry.getKey(), price))
                    .stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  default Optional<TradePrepPrice> resolve(
      PositionPriceResolver resolver, String isin, LocalDate asOfDate) {
    TradePrepPrice known = prices().get(isin);
    if (known != null) {
      return Optional.of(known);
    }
    return resolver.resolve(isin, asOfDate).map(IndexPrice::new);
  }

  record FreshestPerInstrument(Map<String, TradePrepPrice> prices) implements TradePrepPricing {

    @Override
    public LocalDate stalenessReference(LocalDate asOfDate) {
      return asOfDate;
    }
  }

  record OnCommonDate(Map<String, TradePrepPrice> prices, LocalDate date)
      implements TradePrepPricing {

    @Override
    public LocalDate stalenessReference(LocalDate asOfDate) {
      return date;
    }
  }
}
