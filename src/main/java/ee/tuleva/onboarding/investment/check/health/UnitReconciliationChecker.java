package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNIT_RECONCILIATION;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class UnitReconciliationChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund,
      LocalDate navDate,
      List<FundPosition> unitsPositions,
      @Nullable BigDecimal authoritativeUnits,
      @Nullable UnitReconciliationThreshold threshold) {
    if (unitsPositions.isEmpty()) {
      return List.of();
    }
    if (threshold == null) {
      return List.of(
          finding(
              fund,
              WARNING,
              "Unit reconciliation threshold not configured: fund=%s, navDate=%s"
                  .formatted(fund, navDate)));
    }
    if (authoritativeUnits == null) {
      return List.of(
          finding(
              fund,
              WARNING,
              "Unit reconciliation authoritative units unavailable: fund=%s, navDate=%s"
                  .formatted(fund, navDate)));
    }

    var reported = unitsPositions.getFirst().getQuantity();
    var difference = reported.subtract(authoritativeUnits).abs();
    var severity = severityFor(difference, threshold);
    if (severity == null) {
      return List.of();
    }
    return List.of(
        finding(
            fund,
            severity,
            "Unit reconciliation %s: fund=%s, navDate=%s, reported=%s, authoritative=%s, diff=%s, warning=%s, fail=%s"
                .formatted(
                    severity,
                    fund,
                    navDate,
                    reported.toPlainString(),
                    authoritativeUnits.toPlainString(),
                    difference.toPlainString(),
                    threshold.getWarningUnits().toPlainString(),
                    threshold.getFailUnits() == null
                        ? "none"
                        : threshold.getFailUnits().toPlainString())));
  }

  private @Nullable HealthCheckSeverity severityFor(
      BigDecimal difference, UnitReconciliationThreshold threshold) {
    var failUnits = threshold.getFailUnits();
    if (failUnits != null && difference.compareTo(failUnits) > 0) {
      return FAIL;
    }
    if (difference.compareTo(threshold.getWarningUnits()) > 0) {
      return WARNING;
    }
    return null;
  }

  private HealthCheckFinding finding(TulevaFund fund, HealthCheckSeverity severity, String msg) {
    return new HealthCheckFinding(fund, UNIT_RECONCILIATION, severity, msg);
  }
}
