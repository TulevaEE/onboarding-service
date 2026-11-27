package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record UsedPriceOverride(
    PriceOverride override,
    GateNeed need,
    ManualPriceDeviationCheck deviationCheck,
    @Nullable ResolvedPrice indexPrice) {}
