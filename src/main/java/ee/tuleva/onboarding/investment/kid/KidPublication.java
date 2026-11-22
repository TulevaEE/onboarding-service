package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.YearMonth;

record KidPublication(
    TulevaFund fund,
    YearMonth month,
    long calculationId,
    String publishedBy,
    Instant publishedAt) {}
