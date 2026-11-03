package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record KidFigures(
    TulevaFund fund,
    String seriesKey,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal ongoingCharges,
    int recommendedHoldingPeriodYears,
    BigDecimal exampleInvestmentEur,
    List<KidScenario> scenarios,
    List<KidCost> costs) {}
