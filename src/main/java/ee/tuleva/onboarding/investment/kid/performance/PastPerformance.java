package ee.tuleva.onboarding.investment.kid.performance;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

record PastPerformance(
    TulevaFund fund,
    String fundKey,
    @Nullable String benchmarkKey,
    LocalDate asOf,
    boolean enoughHistory,
    List<YearReturn> years) {}
