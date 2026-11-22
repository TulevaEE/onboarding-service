package ee.tuleva.onboarding.investment.kid;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

record KidMonthCalculation(
    YearMonth month,
    long calculationId,
    int version,
    Instant calculatedAt,
    List<String> refusalReasons,
    @Nullable OcfBasis ocf,
    List<PrintedScenario> scenarios,
    List<PrintedCost> costs,
    @Nullable KidPublication published) {

  @JsonProperty
  boolean complete() {
    return refusalReasons.isEmpty();
  }
}
