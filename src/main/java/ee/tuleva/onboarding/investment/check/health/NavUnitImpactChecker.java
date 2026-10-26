package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_UNIT_IMPACT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class NavUnitImpactChecker {

  private static final BigDecimal UNIT_DIFFERENCE_THRESHOLD = new BigDecimal("0.5");
  private static final BigDecimal NEAR_ENOUGH_SHARE_OF_THE_GAP = new BigDecimal("0.1");

  List<HealthCheckFinding> check(
      TulevaFund fund,
      @Nullable BigDecimal reportedUnits,
      @Nullable BigDecimal authoritativeUnits,
      @Nullable BigDecimal aum,
      @Nullable BigDecimal unitsFromCash) {
    if (reportedUnits == null || authoritativeUnits == null) {
      return List.of();
    }

    var difference = reportedUnits.subtract(authoritativeUnits).abs();
    if (difference.compareTo(UNIT_DIFFERENCE_THRESHOLD) <= 0) {
      return List.of();
    }

    // A material unit break we cannot quantify as a NAV impact (missing/zero AUM, or non-positive
    // unit counts) must still FAIL — otherwise the only remaining signal is the WARNING from
    // UnitReconciliationChecker, silently downgrading a potentially price-moving discrepancy.
    if (aum == null
        || aum.signum() <= 0
        || reportedUnits.signum() <= 0
        || authoritativeUnits.signum() <= 0) {
      return List.of(
          new HealthCheckFinding(
              fund,
              NAV_UNIT_IMPACT,
              FAIL,
              ("Unit difference %s exceeds threshold %s but NAV impact is indeterminate"
                      + " (aum=%s, reported=%s, authoritative=%s) — manual check required%s")
                  .formatted(
                      difference.toPlainString(),
                      UNIT_DIFFERENCE_THRESHOLD.toPlainString(),
                      aum == null ? "null" : aum.toPlainString(),
                      reportedUnits.toPlainString(),
                      authoritativeUnits.toPlainString(),
                      cashComparison(reportedUnits, authoritativeUnits, unitsFromCash))));
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
              ("NAV_SEB=%s vs NAV_EPIS=%s = %s%% NAV difference: unitsSeb=%s, unitsEpis=%s,"
                      + " unitsDifference=%s%s")
                  .formatted(
                      navWithReported.toPlainString(),
                      navWithAuthoritative.toPlainString(),
                      navWithReported
                          .subtract(navWithAuthoritative)
                          .divide(navWithAuthoritative, 6, HALF_UP)
                          .multiply(new BigDecimal("100"))
                          .toPlainString(),
                      units(reportedUnits),
                      units(authoritativeUnits),
                      units(reportedUnits.subtract(authoritativeUnits)),
                      cashComparison(reportedUnits, authoritativeUnits, unitsFromCash))));
    }
    return List.of();
  }

  private static String cashComparison(
      BigDecimal reportedUnits, BigDecimal authoritativeUnits, @Nullable BigDecimal unitsFromCash) {
    if (unitsFromCash == null) {
      return "";
    }
    var fromSeb = unitsFromCash.subtract(reportedUnits).abs();
    var fromEpis = unitsFromCash.subtract(authoritativeUnits).abs();
    var sebToEpis = reportedUnits.subtract(authoritativeUnits).abs();
    return ", unitsFromCash=%s%s"
        .formatted(units(unitsFromCash), nearSide(fromSeb, fromEpis, sebToEpis));
  }

  private static String nearSide(BigDecimal fromSeb, BigDecimal fromEpis, BigDecimal sebToEpis) {
    var nearEnoughToBeCloserThanTheOther = sebToEpis.multiply(NEAR_ENOUGH_SHARE_OF_THE_GAP);
    if (fromSeb.compareTo(nearEnoughToBeCloserThanTheOther) <= 0) {
      return " (closer to SEB)";
    }
    if (fromEpis.compareTo(nearEnoughToBeCloserThanTheOther) <= 0) {
      return " (closer to EPIS)";
    }
    return "";
  }

  private static String units(BigDecimal units) {
    return units.stripTrailingZeros().toPlainString();
  }
}
