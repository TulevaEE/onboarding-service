package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.List;

public record GiverCostBasis(BigDecimal remainingUnits, BigDecimal remainingCost) {

  public static GiverCostBasis replay(List<UnitHoldingChange> history) {
    BigDecimal units = ZERO;
    BigDecimal cost = ZERO;

    for (UnitHoldingChange change : history) {
      cost = cost.add(costChangeOf(change, units, cost));
      units = units.add(change.units());
      if (units.signum() == 0) {
        cost = ZERO;
      }
    }

    return new GiverCostBasis(
        units.setScale(FUND_UNIT.getMaxPrecision(), HALF_UP),
        cost.setScale(EUR.getMaxPrecision(), HALF_UP));
  }

  public BigDecimal costOf(BigDecimal units) {
    if (remainingUnits.signum() <= 0) {
      throw new IllegalStateException(
          "The giver holds no units whose cost could follow them: remainingUnits="
              + remainingUnits.toPlainString());
    }
    return shareOf(remainingCost, units, remainingUnits);
  }

  private static BigDecimal costChangeOf(
      UnitHoldingChange change, BigDecimal units, BigDecimal cost) {
    return switch (change.transactionType()) {
      case FUND_SUBSCRIPTION,
          UNIT_TRANSFER,
          ADJUSTMENT,
          REDEMPTION_RESERVED,
          REDEMPTION_CANCELLED ->
          change.cost();
      case REDEMPTION_REQUEST -> shareOf(cost, change.units().negate(), units).negate();
      default ->
          throw new IllegalStateException(
              "Fund units moved under a transaction type their cost cannot be replayed from:"
                  + " transactionId="
                  + change.transactionId()
                  + ", transactionType="
                  + change.transactionType());
    };
  }

  private static BigDecimal shareOf(BigDecimal cost, BigDecimal share, BigDecimal whole) {
    if (share.compareTo(whole) >= 0) {
      return cost;
    }
    return cost.multiply(share).divide(whole, EUR.getMaxPrecision(), HALF_UP);
  }
}
