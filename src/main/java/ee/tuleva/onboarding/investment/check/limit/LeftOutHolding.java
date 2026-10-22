package ee.tuleva.onboarding.investment.check.limit;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record LeftOutHolding(
    String isin, String name, @Nullable BigDecimal holdingValue, String reason)
    implements OwnershipAssessment {}
