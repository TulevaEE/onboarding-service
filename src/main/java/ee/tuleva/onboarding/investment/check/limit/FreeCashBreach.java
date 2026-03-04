package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;

public record FreeCashBreach(
    TulevaFund fund, BigDecimal freeCash, BigDecimal maxFreeCash, BreachSeverity severity) {}
