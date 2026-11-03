package ee.tuleva.onboarding.investment.kid.performance;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record YearReturn(
    int year,
    BigDecimal fundReturn,
    @Nullable BigDecimal benchmarkReturn,
    boolean partialYear,
    boolean yearToDate) {}
