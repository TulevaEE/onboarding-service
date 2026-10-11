package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;

public record FeeComparison(
    BigDecimal currentFeePercent,
    long currentFeeAmount,
    long tulevaFeeAmount,
    long savingsAmount) {}
