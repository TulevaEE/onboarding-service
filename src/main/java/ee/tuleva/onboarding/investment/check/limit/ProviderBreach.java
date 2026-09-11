package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.investment.portfolio.Provider;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;

public record ProviderBreach(
    TulevaFund fund,
    Provider provider,
    BigDecimal actualPercent,
    BigDecimal softLimitPercent,
    BigDecimal hardLimitPercent,
    BreachSeverity severity) {}
