package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record KidFigures(
    TulevaFund fund,
    List<SeriesSegment> series,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal ongoingCharges,
    int recommendedHoldingPeriodYears,
    BigDecimal exampleInvestmentEur,
    List<KidScenario> scenarios,
    List<KidCost> costs) {}
