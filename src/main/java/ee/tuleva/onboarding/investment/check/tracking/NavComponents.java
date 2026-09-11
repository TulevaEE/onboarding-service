package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;

record NavComponents(
    BigDecimal aum, BigDecimal securities, BigDecimal cash, BigDecimal nonSecurityValue) {}
