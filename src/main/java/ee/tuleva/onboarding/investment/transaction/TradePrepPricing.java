package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradePrepPricing() {

  static TradePrepPricing freshest() {
    return new TradePrepPricing();
  }

  Optional<ResolvedPrice> resolve(PositionPriceResolver resolver, String isin, LocalDate asOfDate) {
    return resolver.resolve(isin, asOfDate);
  }
}
