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
