package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

record FeeCheckResult(
    TulevaFund fund,
    LocalDate checkDate,
    @Nullable LocalDate feeMonth,
    List<FeeCheckFinding> findings) {

  boolean hasFails() {
    return findings.stream().anyMatch(f -> f.severity() == FeeCheckSeverity.FAIL);
  }
}
