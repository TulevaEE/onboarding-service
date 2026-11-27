package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;

record ResolvedRate(long id, BigDecimal rebateRate, BigDecimal invoicedFeeRate) {}
