package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradePrepOverrides(Set<String> excludedIsins, List<PriceOverride> prices) {

  static TradePrepOverrides none() {
    return new TradePrepOverrides(Set.of(), List.of());
  }

  static TradePrepOverrides of(TransactionCommand command) {
    return new TradePrepOverrides(command.getExcludeIsins(), command.getPrices());
  }
}
