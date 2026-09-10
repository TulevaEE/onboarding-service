package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatAmount;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatEur;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;
import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatUnits;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

class BreachMessageFormatter {

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
            .formatted(formatEur(flow.unexplained()), formatPercent(flow.unexplainedFraction())));
    if (flow.securityQuantitiesChanged()) {
      sb.append(
          "\n    Trades moved %s EUR at the mark — cash and securities move together, so this nets"
                  .formatted(formatAmount(flow.tradeFlow()))
              + " out of net assets and only the execution difference reaches UNEXPLAINED.");
    } else {
      sb.append("\n    No security quantity changed, so trading cannot explain this.");
    }
    if (flow.marketPnl().signum() == 0) {
      sb.append("\n    No holding moved in price either.");
    }
  }

  private void appendRedemptionCycleSection() {
    sb.append(new RedemptionCycleSection(result, redemptionCycle).describe());
  }

  private void appendSecurityAttributionSection() {
    sb.append(new SecurityAttributionSection(result).describe());
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
}
