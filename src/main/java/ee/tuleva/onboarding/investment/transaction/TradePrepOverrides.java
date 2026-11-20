package ee.tuleva.onboarding.investment.transaction;

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
    List<PriceDeviationConfirmation> confirmedPriceDeviations) {

  static TradePrepOverrides of(TransactionCommand command) {
    return new TradePrepOverrides(
        command.getExcludeIsins(),
        command.getPrices(),
        command.getAcceptedHoldingDifferences(),
        command.getPriceDateRule(),
        command.getConfirmedPriceDeviations());
  }

  List<String> acceptedDifferenceIsins() {
    return acceptedHoldingDifferences == null
        ? List.of()
        : acceptedHoldingDifferences.sortedIsins();
  }
}
