package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatEur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

class RedemptionCycleSection {

  private static final BigDecimal CYCLE_MATCH_TOLERANCE = new BigDecimal("0.05");

  private final TrackingDifferenceResult result;
  private final @Nullable RedemptionCycleHint redemptionCycle;
  private final StringBuilder sb = new StringBuilder();

  RedemptionCycleSection(
      TrackingDifferenceResult result, @Nullable RedemptionCycleHint redemptionCycle) {
    this.result = result;
    this.redemptionCycle = redemptionCycle;
  }

  String describe() {
    var cycle = redemptionCycle;
    if (result.checkType() != MODEL_PORTFOLIO || cycle == null || !cycle.executionDate()) {
      return "";
    }
    sb.append(
        "\n  ⚠️ %s is a PEVA/RAVA execution date — every II pillar switch and exit settles at this NAV."
            .formatted(result.checkDate()));
    var ravaEur = cycle.ravaEur();
    if (!cycle.hasFigures() || ravaEur == null) {
      sb.append(
          "\n     No R17/R21 figures are ingested for this cycle, so the payout cannot be matched"
              + " automatically. Compare the unexplained amount against the RAVA payout by hand.");
      return sb.toString();
    }
    appendFigures(cycle, ravaEur);
    return sb.toString();
  }

  private void appendFigures(RedemptionCycleHint cycle, BigDecimal ravaEur) {
    sb.append("\n     R21 RAVA payout %s".formatted(formatEur(ravaEur)));
    var pikEur = cycle.pikEur();
    if (pikEur != null && pikEur.signum() != 0) {
      sb.append(", R17 PIK %s".formatted(formatEur(pikEur)));
    }
    var flow = result.navFlow();
    if (flow == null) {
      return;
    }
    if (matchesUnexplained(flow.unexplained(), ravaEur)) {
      sb.append(
          "\n     → that is the unexplained amount. Check the redemption payout was booked as a"
              + " liability (payables / pending redemptions).");
    } else {
      sb.append(
          "\n     → does not account for the unexplained amount, so look wider than the"
              + " redemption leg.");
    }
  }

  private static boolean matchesUnexplained(BigDecimal unexplained, BigDecimal ravaEur) {
    if (ravaEur.signum() == 0) {
      return false;
    }
    return unexplained
            .abs()
            .subtract(ravaEur.abs())
            .abs()
            .divide(ravaEur.abs(), 6, RoundingMode.HALF_UP)
            .compareTo(CYCLE_MATCH_TOLERANCE)
        <= 0;
  }
}
