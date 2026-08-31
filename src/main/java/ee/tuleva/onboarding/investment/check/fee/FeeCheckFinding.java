package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record FeeCheckFinding(
    TulevaFund fund,
    FeeCheckType checkType,
    FeeCheckScope scope,
    FeeCheckSeverity severity,
    String message,
    @Nullable BigDecimal deviationAmount,
    Map<String, Object> details) {

  static FeeCheckFinding pass(TulevaFund fund, FeeCheckType checkType, FeeCheckScope scope) {
    return new FeeCheckFinding(fund, checkType, scope, FeeCheckSeverity.PASS, "", null, Map.of());
  }
}
