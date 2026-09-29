package ee.tuleva.onboarding.investment.check.limit;

import java.math.BigDecimal;

record LargestPosition(String isin, BigDecimal percentOfNav) {}
