package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record TradePrepOverrides(
    Set<String> excludedIsins,
    List<PriceOverride> prices,
    @Nullable AcceptedHoldingDifferences acceptedHoldingDifferences,
    PriceDateRule priceDateRule,
    @Nullable BigDecimal cash) {

  static TradePrepOverrides none() {
    return new TradePrepOverrides(
        Set.of(), List.of(), null, PriceDateRule.FRESHEST_PER_INSTRUMENT, null);
  }

  static TradePrepOverrides of(TransactionCommand command) {
    return new TradePrepOverrides(
        command.getExcludeIsins(),
        command.getPrices(),
        command.getAcceptedHoldingDifferences(),
        command.getPriceDateRule(),
        command.getCash());
  }

  List<String> acceptedDifferenceIsins() {
    return acceptedHoldingDifferences == null
        ? List.of()
        : acceptedHoldingDifferences.sortedIsins();
  }
}
