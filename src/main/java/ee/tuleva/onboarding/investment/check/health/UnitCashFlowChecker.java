package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.FindingNumbers.signedEur;
import static ee.tuleva.onboarding.investment.check.health.FindingNumbers.signedUnits;
import static ee.tuleva.onboarding.investment.check.health.FindingNumbers.units;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNIT_CASH_FLOW;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class UnitCashFlowChecker {

  private static final int NAV_PER_UNIT_SCALE = 10;
  private static final int UNIT_SCALE = 3;

  List<HealthCheckFinding> check(
      TulevaFund fund,
      List<FundPosition> positions,
      List<FundPosition> previousPositions,
      PendingTradeChanges changes,
      @Nullable BigDecimal threshold) {
    if (previousPositions.isEmpty()) {
      return List.of();
    }
    if (threshold == null) {
      return notRun(fund, "UNIT_CASH_FLOW_THRESHOLD not configured");
    }
    var unavailableReason = changes.unavailableReason();
    if (unavailableReason != null) {
      return notRun(fund, unavailableReason);
    }
    var today = Snapshot.of(fund, positions);
    if (today.isEmpty()) {
      return notRun(fund, missingUnitsOrCash(positions));
    }
    var before = Snapshot.of(fund, previousPositions).filter(Snapshot::canPriceUnits);
    if (before.isEmpty()) {
      return notRun(fund, missingUnitsOrCash(previousPositions));
    }
    return compare(fund, today.get(), before.get(), changes, threshold);
  }

  private List<HealthCheckFinding> compare(
      TulevaFund fund,
      Snapshot today,
      Snapshot before,
      PendingTradeChanges changes,
      BigDecimal threshold) {
    var navPerUnit = before.aum().divide(before.units(), NAV_PER_UNIT_SCALE, HALF_UP);
    var unitCash =
        today
            .cash()
            .subtract(before.cash())
            .add(changes.disappearedTotal(BUY))
            .subtract(changes.disappearedTotal(SELL))
            .add(today.unitReceivables().subtract(before.unitReceivables()))
            .add(today.redemptionPayables().subtract(before.redemptionPayables()));
    var unitChange = today.units().subtract(before.units());
    var gap = unitChange.multiply(navPerUnit).subtract(unitCash);
    var share = gap.divide(before.aum(), NAV_PER_UNIT_SCALE, HALF_UP).abs();
    if (share.compareTo(threshold) < 0) {
      return List.of();
    }
    var unitsImpliedByCash = before.units().add(unitCash.divide(navPerUnit, UNIT_SCALE, HALF_UP));
    return List.of(
        new HealthCheckFinding(
            fund,
            UNIT_CASH_FLOW,
            WARNING,
            "Units %s but unit cash %s EUR; cash implies %s units, report %s"
                .formatted(
                    signedUnits(unitChange),
                    signedEur(unitCash),
                    units(unitsImpliedByCash),
                    units(today.units()))));
  }

  private static List<HealthCheckFinding> notRun(TulevaFund fund, String reason) {
    return List.of(new HealthCheckFinding(fund, UNIT_CASH_FLOW, NOT_RUN, "Not checked: " + reason));
  }

  private static String missingUnitsOrCash(List<FundPosition> positions) {
    return "the position report as of %s has no unit count or cash"
        .formatted(positions.getFirst().getNavDate());
  }

  private record Snapshot(
      BigDecimal units,
      BigDecimal cash,
      BigDecimal aum,
      BigDecimal unitReceivables,
      BigDecimal redemptionPayables) {

    static Optional<Snapshot> of(TulevaFund fund, List<FundPosition> positions) {
      var units =
          positions.stream()
              .filter(position -> position.getAccountType() == UNITS)
              .map(FundPosition::getQuantity)
              .filter(Objects::nonNull)
              .findFirst();
      var hasCash = positions.stream().anyMatch(position -> position.getAccountType() == CASH);
      if (units.isEmpty() || !hasCash) {
        return Optional.empty();
      }
      return Optional.of(
          new Snapshot(
              units.get(),
              PositionTotals.total(positions, position -> position.getAccountType() == CASH),
              PositionTotals.aum(positions),
              PositionTotals.total(
                  positions,
                  position ->
                      position.getAccountType() == RECEIVABLES
                          && position.isUnitReceivableOf(fund)),
              PositionTotals.total(
                  positions,
                  position ->
                      position.getAccountType() == LIABILITY
                          && position.isRedemptionPayableOf(fund))));
    }

    boolean canPriceUnits() {
      return units.signum() > 0 && aum.signum() > 0;
    }
  }
}
