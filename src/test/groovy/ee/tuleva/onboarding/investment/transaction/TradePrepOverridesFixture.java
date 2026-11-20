package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Set;

final class TradePrepOverridesFixture {

  private TradePrepOverridesFixture() {}

  static TradePrepOverrides noOverrides() {
    return new TradePrepOverrides(
        Set.of(), List.of(), null, PriceDateRule.FRESHEST_PER_INSTRUMENT, null);
  }
}
