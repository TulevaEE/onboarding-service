package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class BreachMessageFormatter {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final TrackingDifferenceResult result;
  private final boolean escalation;
  private final StringBuilder sb = new StringBuilder();

  BreachMessageFormatter(TrackingDifferenceResult result, boolean escalation) {
    this.result = result;
    this.escalation = escalation;
  }

  String format() {
    appendHeader();
    appendActionHint();
    appendNavResidualBreachStatus();
    appendSecurityAttributionSection();
    if (escalation) {
      appendEscalationSection();
    }
    return sb.toString();
  }

  private void appendHeader() {
    sb.append(
        "\n🛑 [%s] %s %s: TD=%s%% (%s=%s%%, benchmark=%s%%)"
            .formatted(
                result.fund(),
                result.checkType(),
                result.checkDate(),
                formatPercent(result.trackingDifference()),
                returnLabel(),
                formatPercent(result.fundReturn()),
                formatPercent(result.benchmarkReturn())));
  }

  private void appendActionHint() {
    if (result.checkType() == MODEL_PORTFOLIO) {
      sb.append("\n  Action: check NAV calculation — weights, prices, cash, fees");
    } else if (result.checkType() == BENCHMARK_MODEL) {
      sb.append(
          "\n  Holdings vs MSCI World/EM index. Regional/ESG spread is expected;"
              + " check an outsized contribution for a stale price.");
    }
  }

  private void appendNavResidualBreachStatus() {
    if (result.checkType() != MODEL_PORTFOLIO) {
      return;
    }
    var navResidual = result.navResidual();
    if (navResidual != null) {
      sb.append(
          "\n  NAV residual: %s%% (%s)"
              .formatted(
                  formatPercent(navResidual),
                  result.navResidualBreach()
                      ? "BLOCKS NAV — investigate pricing / NAV calc"
                      : "non-blocking — fund-vs-model TD explained by trade timing"));
    } else {
      sb.append(
          "\n  NAV residual: not evaluated — begin-of-day holdings unavailable (gate skipped)");
    }
  }

  private void appendSecurityAttributionSection() {
    if (result.securityAttributions().isEmpty()) {
      return;
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
  }

  private void appendBenchmarkModelAttributions(List<SecurityAttribution> sorted) {
    for (var attr : sorted) {
      sb.append(
          "\n  %s: instrument %s%%, index %s%%, contributes %s%% to TD"
              .formatted(
                  attr.isin(),
                  formatPercent(attr.securityReturn()),
                  formatPercent(
                      Objects.requireNonNull(
                          attr.benchmarkReturn(),
                          "Missing benchmark return for BENCHMARK_MODEL attribution: isin="
                              + attr.isin())),
                  formatPercent(attr.contribution())));
    }
  }

  private void appendFundVsModelAttributions(List<SecurityAttribution> sorted) {
    for (var attr : sorted) {
      sb.append(
          "\n  %s: weight %s%%, return %s%%, impact %s%%"
              .formatted(
                  attr.isin(),
                  formatPercent(
                      Objects.requireNonNull(
                          attr.weightDifference(),
                          "Missing weight difference for attribution: isin=" + attr.isin())),
                  formatPercent(attr.securityReturn()),
                  formatPercent(attr.contribution())));
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

  private void appendEscalationSection() {
    sb.append(
        "\n  [%d consecutive days, compounded TD=%s%%]"
            .formatted(result.consecutiveBreachDays(), formatPercent(result.consecutiveNetTd())));

    if (result.compoundedFundReturn() != null && result.compoundedBenchmarkReturn() != null) {
      sb.append(
          "\n  Compounded: fund=%s%%, benchmark=%s%%"
              .formatted(
                  formatPercent(result.compoundedFundReturn()),
                  formatPercent(result.compoundedBenchmarkReturn())));
    }

    appendMultiDayAttribution();

    if (result.escalationCashDrag() != null && result.escalationCashDrag().signum() != 0) {
      sb.append("\n    Cash drag: %s%%".formatted(formatPercent(result.escalationCashDrag())));
    }
    if (result.escalationFeeDrag() != null && result.escalationFeeDrag().signum() != 0) {
      sb.append("\n    Fee drag: %s%%".formatted(formatPercent(result.escalationFeeDrag())));
    }
    if (result.escalationResidual() != null && result.escalationResidual().signum() != 0) {
      sb.append("\n    Residual: %s%%".formatted(formatPercent(result.escalationResidual())));
    }
  }

  private void appendMultiDayAttribution() {
    if (result.escalationAttributions() == null || result.escalationAttributions().isEmpty()) {
      return;
    }
    sb.append("\n  Multi-day attribution (arithmetic sum of daily contributions):");
    var sorted =
        result.escalationAttributions().entrySet().stream()
            .sorted(
                Comparator.comparing(
                    (Map.Entry<String, BigDecimal> e) -> e.getValue().abs(),
                    Comparator.reverseOrder()))
            .toList();
    for (var entry : sorted) {
      sb.append("\n    %s: %s%%".formatted(entry.getKey(), formatPercent(entry.getValue())));
    }
  }

  private String returnLabel() {
    return result.checkType() == BENCHMARK_MODEL ? "holdings" : "fund";
  }

  private String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
