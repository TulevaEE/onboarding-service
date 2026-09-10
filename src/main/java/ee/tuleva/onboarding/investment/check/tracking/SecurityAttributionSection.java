package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

class SecurityAttributionSection {

  private final TrackingDifferenceResult result;
  private final StringBuilder sb = new StringBuilder();

  SecurityAttributionSection(TrackingDifferenceResult result) {
    this.result = result;
  }

  String describe() {
    if (result.securityAttributions().isEmpty()) {
      return "";
    }
    var sorted =
        result.securityAttributions().stream()
            .sorted(
                Comparator.comparing(
                    (SecurityAttribution a) -> a.contribution().abs(), Comparator.reverseOrder()))
            .toList();

    if (result.checkType() == BENCHMARK_MODEL) {
      appendBenchmarkModelAttributions(sorted);
    } else {
      appendFundVsModelAttributions(sorted);
    }
    return sb.toString();
  }

  private void appendBenchmarkModelAttributions(List<SecurityAttribution> sorted) {
    for (var attr : sorted) {
      sb.append(
          "\n  %s: instrument %s%%, index %s%%, contributes %s%% to TD"
              .formatted(
                  attr.isin(),
                  formatPercent(attr.securityReturn()),
                  formatPercent(benchmarkReturn(attr)),
                  formatPercent(attr.contribution())));
      if (cancelsOut(attr)) {
        sb.append(" — stale on both legs, cancels out — not the cause");
      }
    }
  }

  private static boolean cancelsOut(SecurityAttribution attr) {
    return attr.securityReturn().signum() == 0 && benchmarkReturn(attr).signum() == 0;
  }

  private void appendFundVsModelAttributions(List<SecurityAttribution> sorted) {
    for (var attr : sorted) {
      sb.append(
          "\n  %s: weight %s%%, return %s%%, impact %s%%"
              .formatted(
                  attr.isin(),
                  formatPercent(weightDifference(attr)),
                  formatPercent(attr.securityReturn()),
                  formatPercent(attr.contribution())));
      if (attr.securityReturn().signum() == 0) {
        sb.append(" — same price both days, contributes nothing to the residual");
      }
    }

    if (result.cashDrag().signum() != 0) {
      sb.append("\n  Cash drag: %s%%".formatted(formatPercent(result.cashDrag())));
    }
    if (result.feeDrag().signum() != 0) {
      sb.append("\n  Fee drag: %s%%".formatted(formatPercent(result.feeDrag())));
    }
    if (result.residual().signum() != 0) {
      sb.append("\n  Residual: %s%%".formatted(formatPercent(result.residual())));
    }
  }

  private static BigDecimal benchmarkReturn(SecurityAttribution attr) {
    return Objects.requireNonNull(
        attr.benchmarkReturn(),
        "Missing benchmark return for BENCHMARK_MODEL attribution: isin=" + attr.isin());
  }

  private static BigDecimal weightDifference(SecurityAttribution attr) {
    return Objects.requireNonNull(
        attr.weightDifference(), "Missing weight difference for attribution: isin=" + attr.isin());
  }
}
