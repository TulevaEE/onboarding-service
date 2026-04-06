package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;

record SecurityAttribution(
    String isin,
    BigDecimal modelWeight,
    BigDecimal actualWeight,
    BigDecimal weightDifference,
    BigDecimal securityReturn,
    BigDecimal contribution) {}
