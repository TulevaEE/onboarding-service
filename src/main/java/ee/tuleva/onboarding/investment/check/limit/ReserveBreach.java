package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;

public record ReserveBreach(
    TulevaFund fund,
    BigDecimal cashBalance,
    BigDecimal reserveSoft,
    BigDecimal reserveHard,
    BreachSeverity severity) {}
