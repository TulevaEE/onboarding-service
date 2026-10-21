package ee.tuleva.onboarding.investment.check.health;

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

final class UnitsFromCash {

  private static final int NAV_PER_UNIT_SCALE = 10;
  private static final int UNIT_SCALE = 3;

  private UnitsFromCash() {}

  static Optional<BigDecimal> of(
      TulevaFund fund,
      List<FundPosition> positions,
      List<FundPosition> previousPositions,
      OpenTradeChanges tradeChanges) {
    if (tradeChanges.unavailableReason() != null) {
      return Optional.empty();
    }
    var today = Day.of(fund, positions);
    var before = Day.of(fund, previousPositions).filter(Day::canPriceAUnit);
    if (today.isEmpty() || before.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(unitsFromCash(today.get(), before.get(), tradeChanges));
  }

  private static BigDecimal unitsFromCash(Day today, Day before, OpenTradeChanges tradeChanges) {
    var navPerUnit = before.netAssets().divide(before.units(), NAV_PER_UNIT_SCALE, HALF_UP);
    var cashForUnits =
        today
            .cash()
            .subtract(before.cash())
            .add(tradeChanges.disappearedTotal(BUY))
            .subtract(tradeChanges.disappearedTotal(SELL))
            .add(today.unitReceivables().subtract(before.unitReceivables()))
            .add(today.redemptionPayables().subtract(before.redemptionPayables()));
    return before.units().add(cashForUnits.divide(navPerUnit, UNIT_SCALE, HALF_UP));
  }

  private record Day(
      BigDecimal units,
      BigDecimal cash,
      BigDecimal netAssets,
      BigDecimal unitReceivables,
      BigDecimal redemptionPayables) {

    static Optional<Day> of(TulevaFund fund, List<FundPosition> positions) {
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
          new Day(
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

    boolean canPriceAUnit() {
      return units.signum() > 0 && netAssets.signum() > 0;
    }
  }
}
