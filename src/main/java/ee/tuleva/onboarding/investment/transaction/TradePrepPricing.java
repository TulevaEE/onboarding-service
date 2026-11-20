package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradePrepPricing(Map<String, BigDecimal> manualPrices) {

  static TradePrepPricing freshest() {
    return new TradePrepPricing(Map.of());
  }

  Optional<ResolvedPrice> resolve(PositionPriceResolver resolver, String isin, LocalDate asOfDate) {
    BigDecimal manualPrice = manualPrices.get(isin);
    if (manualPrice != null) {
      return Optional.of(
          ResolvedPrice.builder().usedPrice(manualPrice).validationStatus(OK).build());
    }
    return resolver.resolve(isin, asOfDate);
  }
}
