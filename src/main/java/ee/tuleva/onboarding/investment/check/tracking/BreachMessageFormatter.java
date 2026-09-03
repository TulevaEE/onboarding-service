package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

class BreachMessageFormatter {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private static final BigDecimal CYCLE_MATCH_TOLERANCE = new BigDecimal("0.05");

  private final TrackingDifferenceResult result;
  private final boolean escalation;
  private final @Nullable RedemptionCycleHint redemptionCycle;
  private final StringBuilder sb = new StringBuilder();

  BreachMessageFormatter(
      TrackingDifferenceResult result,
      boolean escalation,
      @Nullable RedemptionCycleHint redemptionCycle) {
    this.result = result;
    this.escalation = escalation;
    this.redemptionCycle = redemptionCycle;
  }

  String format() {
    appendHeader();
    appendActionHint();
    appendNavResidualBreachStatus();
    appendNavFlowSection();
    appendRedemptionCycleSection();
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
    if (result.checkType() == BENCHMARK_MODEL) {
      sb.append(
          "\n  Holdings vs MSCI World/EM index. Regional/ESG spread is expected;"
              + " check an outsized contribution for a stale price.");
      return;
    }
    if (result.checkType() != MODEL_PORTFOLIO) {
      return;
    }
    var flow = result.navFlow();
    if (flow == null) {
      sb.append("\n  Action: check NAV calculation — weights, prices, cash, fees");
      return;
    }
    if (!result.navResidualBreach()) {
      sb.append(
          "\n  Action: the NAV itself reconciles (%s EUR unexplained) — the gap is model-vs-fund"
                  .formatted(formatAmount(flow.unexplained()))
              + " weighting, not the NAV calculation.");
      return;
    }
    if (flow.securityQuantitiesChanged()) {
      sb.append(
          "\n  Action: %s EUR unexplained. Quantities moved today — check trade settlement (cash"
                  .formatted(formatAmount(flow.unexplained()))
              + " paid vs marked value), then cash, units and the liability lines.");
      return;
    }
    sb.append(
        "\n  Action: %s EUR unexplained and no quantity moved — this is not prices and not trading."
                .formatted(formatAmount(flow.unexplained()))
            + " Check cash, units outstanding and the liability lines (payables, pending"
            + " redemptions).");
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
                      ? "BLOCKS NAV — report held, not sent to the trustee"
                      : "non-blocking — fund-vs-model TD explained by trade timing"));
    } else {
      sb.append(
          "\n  NAV residual: not evaluated — begin-of-day holdings unavailable (gate skipped)");
    }
  }

  private void appendNavFlowSection() {
    var flow = result.navFlow();
    if (result.checkType() != MODEL_PORTFOLIO || flow == null) {
      return;
    }
    var expectedClosing =
        flow.openingNetAssets()
            .add(flow.marketPnl())
            .add(flow.unitFlow())
            .subtract(flow.feeAccrual());
    sb.append("\n  NAV bridge (EUR):");
    sb.append("\n    opening net assets   %s".formatted(formatEur(flow.openingNetAssets())));
    sb.append("\n    market P&L           %s".formatted(formatEur(flow.marketPnl())));
    sb.append(
        "\n    unit flow            %s  (%s units)"
            .formatted(formatEur(flow.unitFlow()), formatUnits(flow.unitsChange())));
    sb.append("\n    fee accrual          %s".formatted(formatEur(flow.feeAccrual().negate())));
    sb.append("\n    expected closing     %s".formatted(formatEur(expectedClosing)));
    sb.append("\n    actual closing       %s".formatted(formatEur(flow.closingNetAssets())));
    sb.append(
        "\n    UNEXPLAINED          %s  (%s%% of opening)"
            .formatted(
                formatEur(flow.unexplained()),
                formatPercent(
                    flow.unexplained().divide(flow.openingNetAssets(), 6, RoundingMode.HALF_UP))));
    if (!flow.securityQuantitiesChanged()) {
      sb.append("\n    No security quantity changed, so trading cannot explain this.");
    }
    if (flow.marketPnl().signum() == 0) {
      sb.append("\n    No holding moved in price either.");
    }
  }

  private void appendRedemptionCycleSection() {
    if (result.checkType() != MODEL_PORTFOLIO
        || redemptionCycle == null
        || !redemptionCycle.executionDate()) {
      return;
    }
    sb.append(
        "\n  ⚠️ %s is a PEVA/RAVA execution date — every II pillar switch and exit settles at this NAV."
            .formatted(result.checkDate()));
    var flow = result.navFlow();
    var ravaEur = redemptionCycle.ravaEur();
    if (!redemptionCycle.hasFigures() || ravaEur == null) {
      sb.append(
          "\n     No R17/R21 figures are ingested for this cycle, so the payout cannot be matched"
              + " automatically. Compare the unexplained amount against the RAVA payout by hand.");
      return;
    }
    sb.append("\n     R21 RAVA payout %s".formatted(formatEur(ravaEur)));
    var pikEur = redemptionCycle.pikEur();
    if (pikEur != null && pikEur.signum() != 0) {
      sb.append(", R17 PIK %s".formatted(formatEur(pikEur)));
    }
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

  private static String formatEur(BigDecimal value) {
    return String.format("%18s", formatAmount(value));
  }

  private static String formatAmount(BigDecimal value) {
    return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.UK)).format(value);
  }

  private static String formatUnits(BigDecimal value) {
    var formatter =
        new DecimalFormat("+#,##0.###;-#,##0.###", DecimalFormatSymbols.getInstance(Locale.UK));
    return formatter.format(value);
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
