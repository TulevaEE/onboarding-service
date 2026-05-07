package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record SecurityAttribution(
    String isin,
    @Nullable BigDecimal modelWeight,
    @Nullable BigDecimal actualWeight,
    @Nullable BigDecimal weightDifference,
    BigDecimal securityReturn,
    @Nullable BigDecimal benchmarkReturn,
    BigDecimal contribution) {}
