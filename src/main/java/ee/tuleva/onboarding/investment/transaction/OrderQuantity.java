package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record OrderQuantity(
    @Nullable BigDecimal quantity,
    @Nullable String stalePriceComment,
    @Nullable ResolvedPrice resolvedPrice) {}
