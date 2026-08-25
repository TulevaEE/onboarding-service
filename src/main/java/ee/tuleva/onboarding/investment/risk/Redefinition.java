package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record Redefinition(
    LocalDate date,
    @Nullable String previousHoldingPeriodTradingDays,
    String holdingPeriodTradingDays) {}
