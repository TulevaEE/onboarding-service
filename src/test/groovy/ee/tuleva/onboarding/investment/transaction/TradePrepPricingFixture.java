package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.FreshestPerInstrument;
import java.util.Map;

final class TradePrepPricingFixture {

  private TradePrepPricingFixture() {}

  static TradePrepPricing noResolvedPrices() {
    return new FreshestPerInstrument(Map.of());
  }
}
