package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
sealed interface TradePrepPricing {

  Map<String, TradePrepPrice> prices();

  LocalDate lookupDate(LocalDate asOfDate);

  default Optional<TradePrepPrice> resolve(
      PositionPriceResolver resolver, String isin, LocalDate asOfDate) {
    TradePrepPrice known = prices().get(isin);
    if (known != null) {
      return Optional.of(known);
    }
    return resolver.resolve(isin, lookupDate(asOfDate)).map(IndexPrice::new);
  }

  record FreshestPerInstrument(Map<String, TradePrepPrice> prices) implements TradePrepPricing {

    @Override
    public LocalDate lookupDate(LocalDate asOfDate) {
      return asOfDate;
    }
  }

  record OnCommonDate(Map<String, TradePrepPrice> prices, LocalDate date)
      implements TradePrepPricing {

    @Override
    public LocalDate lookupDate(LocalDate asOfDate) {
      return date;
    }
  }
}
