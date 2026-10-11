package ee.tuleva.onboarding.savings.fund.nav;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

record NavRevision(BigDecimal publishedNavPerUnit, int changedPositionRows) {

  String describe(NavCalculationResult revised) {
    return "Position report re-imported with %d changed rows: publishedNav=%s, revisedNav=%s (%s%%)"
        .formatted(
            changedPositionRows,
            publishedNavPerUnit.toPlainString(),
            revised.navPerUnit().toPlainString(),
            changePercent(revised.navPerUnit()).toPlainString());
  }

  private BigDecimal changePercent(BigDecimal revisedNavPerUnit) {
    return revisedNavPerUnit
        .subtract(publishedNavPerUnit)
        .multiply(BigDecimal.valueOf(100))
        .divide(publishedNavPerUnit, 2, HALF_UP);
  }
}
