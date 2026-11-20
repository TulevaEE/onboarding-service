package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record GateCheck(
    List<InputGateFailure> failures,
    TradePrepPricing pricing,
    List<UsedPriceOverride> priceOverrides) {}
