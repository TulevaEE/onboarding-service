package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeComparison(
    BigDecimal currentFeePercent,
    long currentFeeAmount,
    long tulevaFeeAmount,
    long savingsAmount,
    BigDecimal tulevaFeePercent,
    long assetsAmount,
    LocalDate asOf) {}
