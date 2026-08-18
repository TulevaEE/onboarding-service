package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;

public record FeeBases(BigDecimal navFeeBase, BigDecimal assetValue) {}
