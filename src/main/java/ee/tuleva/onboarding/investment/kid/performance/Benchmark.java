package ee.tuleva.onboarding.investment.kid.performance;

import ee.tuleva.onboarding.comparisons.fundvalue.EquityCap;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record Benchmark(String key, @Nullable EquityCap equityCap) {

  @Nullable EquityCap equityCapHeldInAYearFrom(int firstYear, LocalDate fundStart) {
    return equityCap != null && equityCap.heldInAYearFrom(firstYear, fundStart) ? equityCap : null;
  }
}
