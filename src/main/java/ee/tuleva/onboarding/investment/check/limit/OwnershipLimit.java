package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

record OwnershipLimit(
    TulevaFund fund,
    LocalDate effectiveDate,
    BigDecimal softLimitPercent,
    BigDecimal hardLimitPercent) {}
