package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record AcceptedHoldingDifference(
    String isin,
    GateNeed need,
    @Nullable BigDecimal registryQuantity,
    @Nullable BigDecimal reportQuantity) {}
