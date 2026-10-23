package ee.tuleva.onboarding.investment.kid;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record KidScenario(
    int horizonYears,
    ScenarioKind kind,
    BigDecimal amountEur,
    BigDecimal averageReturnPerYear,
    LocalDate periodStart,
    LocalDate periodEnd,
    WindowSource window,
    @Nullable Integer windowMonths) {}
