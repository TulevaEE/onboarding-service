package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record UsedPriceOverride(
    PriceOverride override,
    GateNeed need,
    ManualPriceDeviationCheck deviationCheck,
    @Nullable ResolvedPrice indexPrice) {

  UsedPriceOverride checkedAgainst(PriceDeviations deviations) {
    return deviations.withoutIndexPriceHistory().contains(override.isin())
        ? new UsedPriceOverride(
            override, need, ManualPriceDeviationCheck.NO_INDEX_PRICE_HISTORY, indexPrice)
        : this;
  }
}
