package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.FindingNumbers.eur;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class UnsettledTradeChecker {

  private static final BigDecimal CENT = new BigDecimal("0.01");

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> receivables,
      List<FundPosition> liabilities,
      PendingTradeReport pendingReport,
      PendingTradeChanges changes,
      @Nullable BigDecimal aum,
      @Nullable BigDecimal units) {
    var reason = pendingReport.unavailableReason(fund);
    if (reason.isPresent()) {
      return List.of(
          new HealthCheckFinding(fund, UNSETTLED_TRADES, NOT_RUN, "Not checked: " + reason.get()));
    }

    var payables =
        new Gap(
            "Trade payables",
            PositionTotals.total(liabilities, FundPosition::isTradePayable).negate(),
            "pending buys",
            pendingReport.totalOf(fund, BUY),
            BUY);
    var tradeReceivables =
        new Gap(
            "Trade receivables",
            PositionTotals.total(receivables, FundPosition::isTradeReceivable),
            "pending sells",
            pendingReport.totalOf(fund, SELL),
            SELL);

    var gaps = Stream.of(payables, tradeReceivables).filter(Gap::isMaterial).toList();
    if (gaps.isEmpty()) {
      return List.of();
    }

    var aumCorrection = payables.difference().subtract(tradeReceivables.difference());
    var severity = movesNavPerUnit(fund, aum, units, aumCorrection) ? FAIL : WARNING;
    return gaps.stream()
        .map(gap -> new HealthCheckFinding(fund, UNSETTLED_TRADES, severity, gap.describe(changes)))
        .toList();
  }

  private static boolean movesNavPerUnit(
      TulevaFund fund,
      @Nullable BigDecimal aum,
      @Nullable BigDecimal units,
      BigDecimal aumCorrection) {
    if (aum == null || units == null || aum.signum() <= 0 || units.signum() <= 0) {
      return true;
    }
    var scale = fund.getNavScale();
    return aum.divide(units, scale, HALF_UP)
            .compareTo(aum.add(aumCorrection).divide(units, scale, HALF_UP))
        != 0;
  }

  private record Gap(
      String reportedLabel,
      BigDecimal reported,
      String pendingLabel,
      BigDecimal pending,
      TransactionType side) {

    BigDecimal difference() {
      return reported.subtract(pending);
    }

    boolean isMaterial() {
      return difference().abs().compareTo(CENT) >= 0;
    }

    String describe(PendingTradeChanges changes) {
      var text =
          "%s %s, %s %s, diff %s"
              .formatted(
                  reportedLabel, eur(reported), pendingLabel, eur(pending), eur(difference()));
      return settledTradeCoveringTheGap(changes)
          .map(trade -> text + " = " + settled(trade))
          .orElse(text);
    }

    private Optional<PendingTrade> settledTradeCoveringTheGap(PendingTradeChanges changes) {
      return changes.disappeared().stream()
          .filter(trade -> trade.side() == side)
          .filter(trade -> trade.total().subtract(difference()).abs().compareTo(CENT) < 0)
          .findFirst();
    }

    private static String settled(PendingTrade trade) {
      var name = Objects.requireNonNullElse(trade.reference(), trade.isin());
      var settlementDate = trade.settlementDate();
      return settlementDate == null ? name : "%s, settled %s".formatted(name, settlementDate);
    }
  }
}
