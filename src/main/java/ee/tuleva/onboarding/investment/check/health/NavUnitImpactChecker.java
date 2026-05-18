package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_UNIT_IMPACT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class NavUnitImpactChecker {

  private static final BigDecimal UNIT_DIFFERENCE_THRESHOLD = new BigDecimal("0.5");

  List<HealthCheckFinding> check(
      TulevaFund fund,
      @Nullable BigDecimal reportedUnits,
      @Nullable BigDecimal authoritativeUnits,
      @Nullable BigDecimal aum) {
    if (reportedUnits == null || authoritativeUnits == null) {
      return List.of();
    }

    var difference = reportedUnits.subtract(authoritativeUnits).abs();
    if (difference.compareTo(UNIT_DIFFERENCE_THRESHOLD) <= 0) {
      return List.of();
    }

    if (aum == null
        || aum.signum() <= 0
        || reportedUnits.signum() <= 0
        || authoritativeUnits.signum() <= 0) {
      return List.of();
    }

    var navScale = fund.getNavScale();
    var navWithReported = aum.divide(reportedUnits, navScale, HALF_UP);
    var navWithAuthoritative = aum.divide(authoritativeUnits, navScale, HALF_UP);

    if (navWithReported.compareTo(navWithAuthoritative) != 0) {
      return List.of(
          new HealthCheckFinding(
              fund,
              NAV_UNIT_IMPACT,
              FAIL,
              "NAV_SEB=%s vs NAV_EPIS=%s = %s%% NAV difference"
                  .formatted(
                      navWithReported.toPlainString(),
                      navWithAuthoritative.toPlainString(),
                      navWithReported
                          .subtract(navWithAuthoritative)
                          .divide(navWithAuthoritative, 6, HALF_UP)
                          .multiply(new BigDecimal("100"))
                          .toPlainString())));
    }
    return List.of();
  }
}
