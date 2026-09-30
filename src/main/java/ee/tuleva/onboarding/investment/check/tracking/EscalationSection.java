package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;

import ee.tuleva.onboarding.investment.check.tracking.ConsecutiveBreachTracker.ConsecutiveBreachInfo;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record EscalationSection(
    int days,
    int uncheckedDays,
    BigDecimal netTd,
    @Nullable BigDecimal compoundedFundReturn,
    @Nullable BigDecimal compoundedBenchmarkReturn,
    @Nullable Map<String, BigDecimal> attributions,
    @Nullable BigDecimal cashDrag,
    @Nullable BigDecimal feeDrag,
    @Nullable BigDecimal residual) {

  static EscalationSection throughToday(TrackingDifferenceResult result) {
    return new EscalationSection(
        result.consecutiveBreachDays(),
        result.escalationUncheckedDays(),
        result.consecutiveNetTd(),
        result.compoundedFundReturn(),
        result.compoundedBenchmarkReturn(),
        result.escalationAttributions(),
        result.escalationCashDrag(),
        result.escalationFeeDrag(),
        result.escalationResidual());
  }

  static EscalationSection ofEndedStreak(ConsecutiveBreachInfo streak) {
    return new EscalationSection(
        streak.count(),
        streak.uncheckedDays(),
        streak.compoundedTd(),
        streak.compoundedFundReturn(),
        streak.compoundedBenchmarkReturn(),
        streak.contributionByIsin(),
        streak.cashDragSum(),
        streak.feeDragSum(),
        streak.residualSum());
  }

  String describe() {
    var sb = new StringBuilder();
    if (uncheckedDays > 0) {
      sb.append(
          "\n  [%d consecutive days, %d of them with no check, compounded TD over the checked days=%s%%]"
              .formatted(days, uncheckedDays, formatPercent(netTd)));
    } else {
      sb.append(
          "\n  [%d consecutive days, compounded TD=%s%%]".formatted(days, formatPercent(netTd)));
    }
    if (compoundedFundReturn != null && compoundedBenchmarkReturn != null) {
      sb.append(
          "\n  Compounded: fund=%s%%, benchmark=%s%%"
              .formatted(
                  formatPercent(compoundedFundReturn), formatPercent(compoundedBenchmarkReturn)));
    }
    appendMultiDayAttribution(sb);
    appendNonZero(sb, "Cash drag", cashDrag);
    appendNonZero(sb, "Fee drag", feeDrag);
    appendNonZero(sb, "Residual", residual);
    return sb.toString();
  }

  private void appendMultiDayAttribution(StringBuilder sb) {
    if (attributions == null || attributions.isEmpty()) {
      return;
    }
    sb.append("\n  Multi-day attribution (arithmetic sum of daily contributions):");
    attributions.entrySet().stream()
        .sorted(
            Comparator.comparing(
                (Map.Entry<String, BigDecimal> e) -> e.getValue().abs(), Comparator.reverseOrder()))
        .forEach(
            entry ->
                sb.append(
                    "\n    %s: %s%%".formatted(entry.getKey(), formatPercent(entry.getValue()))));
  }

  private static void appendNonZero(StringBuilder sb, String label, @Nullable BigDecimal value) {
    if (value != null && value.signum() != 0) {
      sb.append("\n    %s: %s%%".formatted(label, formatPercent(value)));
    }
  }
}
