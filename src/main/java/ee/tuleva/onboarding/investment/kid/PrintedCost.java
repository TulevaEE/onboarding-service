package ee.tuleva.onboarding.investment.kid;

import java.math.BigDecimal;

record PrintedCost(
    int horizonYears,
    BigDecimal totalCostsEur,
    BigDecimal annualCostImpact,
    BigDecimal returnPerYearBeforeCosts,
    BigDecimal returnPerYearAfterCosts) {}
