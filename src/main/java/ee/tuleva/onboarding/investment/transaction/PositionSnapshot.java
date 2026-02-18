package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;

public record PositionSnapshot(String isin, BigDecimal marketValue) {}
