package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;

public record PositionBreach(
    TulevaFund fund,
    String isin,
    String label,
    BigDecimal actualPercent,
    BigDecimal softLimitPercent,
    BigDecimal hardLimitPercent,
    BreachSeverity severity) {}
