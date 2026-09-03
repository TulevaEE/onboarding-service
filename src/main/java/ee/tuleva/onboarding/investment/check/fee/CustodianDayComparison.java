package ee.tuleva.onboarding.investment.check.fee;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record CustodianDayComparison(
    LocalDate navDate,
    List<CustodianLineDifference> differences,
    boolean navPredatesReport,
    BigDecimal navImpact,
    BigDecimal navImpactBasisPoints) {

  boolean matches() {
    return differences.isEmpty();
  }

  boolean needsNoCorrection(BigDecimal materialBasisPoints) {
    return navPredatesReport && !movesTheNavMoreThan(materialBasisPoints);
  }

  boolean movesTheNavMoreThan(BigDecimal materialBasisPoints) {
    return navImpactBasisPoints.abs().compareTo(materialBasisPoints) > 0;
  }

  BigDecimal totalDifference() {
    return differences.stream()
        .map(CustodianLineDifference::difference)
        .reduce(ZERO, BigDecimal::add);
  }

  String describeLines() {
    return differences.stream()
        .map(difference -> navDate + " · " + difference)
        .reduce((first, second) -> first + "\n" + second)
        .orElse(navDate.toString());
  }
}
