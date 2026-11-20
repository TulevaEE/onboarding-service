package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PriceCheck(
    Optional<InputGateFailure> missingPrices,
    TradePrepPricing pricing,
    List<UsedPriceOverride> priceOverrides) {}
