package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;

public record PositionLimitSnapshot(BigDecimal softLimit, BigDecimal hardLimit) {}
