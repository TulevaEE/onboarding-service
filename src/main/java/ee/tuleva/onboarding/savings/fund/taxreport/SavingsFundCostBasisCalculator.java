package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.WEIGHTED_AVERAGE;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.account.transaction.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SavingsFundCostBasisCalculator {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final MathContext UNIT_COST_PRECISION = MathContext.DECIMAL128;
  private static final BigDecimal UNIT_EPSILON = new BigDecimal("0.000000001");
  private static final int MONEY_SCALE = 2;

  private record Lot(BigDecimal units, BigDecimal unitCost) {}

  private record Consumption(BigDecimal acquisitionCost, List<Lot> remainingLots) {}

  public List<RealisedGain> realisedGainsBetween(
      List<Transaction> transactions, LocalDate from, LocalDate to, CostBasisMethod method) {
    return realiseUpTo(transactions, to, method).stream()
        .filter(gain -> !dayOf(gain.time()).isBefore(from))
        .toList();
  }

  private List<RealisedGain> realiseUpTo(
      List<Transaction> transactions, LocalDate to, CostBasisMethod method) {
    List<Lot> lots = new ArrayList<>();
    List<RealisedGain> realised = new ArrayList<>();

    transactions.stream()
        .filter(transaction -> !dayOf(transaction.time()).isAfter(to))
        .sorted(comparing(Transaction::time))
        .forEach(
            transaction -> {
              BigDecimal units = requireUnits(transaction);

              if (transaction.isAcquisition()) {
                lots.add(new Lot(units, unitCostPaid(transaction, units)));
                return;
              }

              requireEnoughHeldUnits(transaction, units, lots);

              Consumption consumption = consume(lots, units, method);
              lots.clear();
              lots.addAll(consumption.remainingLots());
              realised.add(toRealisedGain(transaction, units, consumption.acquisitionCost()));
            });

    return List.copyOf(realised);
  }

  private static BigDecimal requireUnits(Transaction transaction) {
    BigDecimal units = transaction.units();

    if (units == null) {
      throw new IllegalStateException(
          "Savings fund transaction missing units: id=%s, time=%s"
              .formatted(transaction.id(), transaction.time()));
    }

    if (units.signum() == 0) {
      throw new IllegalStateException(
          "Savings fund transaction has zero units: id=%s, time=%s"
              .formatted(transaction.id(), transaction.time()));
    }

    return units.abs();
  }

  private static void requireEnoughHeldUnits(
      Transaction transaction, BigDecimal units, List<Lot> lots) {
    BigDecimal heldUnits = totalUnits(lots);

    if (units.subtract(heldUnits).compareTo(UNIT_EPSILON) > 0) {
      throw new IllegalStateException(
          "Redemption exceeds held units: id=%s, time=%s, requested=%s, held=%s"
              .formatted(transaction.id(), transaction.time(), units, heldUnits));
    }
  }

  private static BigDecimal unitCostPaid(Transaction transaction, BigDecimal units) {
    return transaction.amount().abs().divide(units, UNIT_COST_PRECISION);
  }

  private static RealisedGain toRealisedGain(
      Transaction transaction, BigDecimal units, BigDecimal acquisitionCost) {
    BigDecimal proceeds = transaction.amount().abs().setScale(MONEY_SCALE, HALF_UP);
    BigDecimal cost = acquisitionCost.setScale(MONEY_SCALE, HALF_UP);

    return RealisedGain.builder()
        .time(transaction.time())
        .units(units)
        .acquisitionCost(cost)
        .proceeds(proceeds)
        .gain(proceeds.subtract(cost))
        .build();
  }

  private static Consumption consume(List<Lot> lots, BigDecimal units, CostBasisMethod method) {
    return method == WEIGHTED_AVERAGE
        ? consumeAtAverageCost(lots, units)
        : consumeOldestFirst(lots, units);
  }

  private static Consumption consumeAtAverageCost(List<Lot> lots, BigDecimal units) {
    BigDecimal heldUnits = totalUnits(lots);
    BigDecimal averageUnitCost =
        heldUnits.signum() > 0
            ? totalCost(lots).divide(heldUnits, UNIT_COST_PRECISION)
            : BigDecimal.ZERO;
    BigDecimal unitsLeft = heldUnits.subtract(units);

    return new Consumption(
        units.multiply(averageUnitCost),
        unitsLeft.compareTo(UNIT_EPSILON) > 0
            ? List.of(new Lot(unitsLeft, averageUnitCost))
            : List.of());
  }

  private static Consumption consumeOldestFirst(List<Lot> lots, BigDecimal units) {
    List<Lot> remainingLots = new ArrayList<>();
    BigDecimal remaining = units;
    BigDecimal cost = BigDecimal.ZERO;

    for (Lot lot : lots) {
      BigDecimal taken = lot.units().min(remaining.max(BigDecimal.ZERO));
      cost = cost.add(taken.multiply(lot.unitCost()));
      remaining = remaining.subtract(taken);
      BigDecimal leftInLot = lot.units().subtract(taken);
      if (leftInLot.compareTo(UNIT_EPSILON) > 0) {
        remainingLots.add(new Lot(leftInLot, lot.unitCost()));
      }
    }

    return new Consumption(cost, List.copyOf(remainingLots));
  }

  private static BigDecimal totalUnits(List<Lot> lots) {
    return lots.stream().map(Lot::units).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal totalCost(List<Lot> lots) {
    return lots.stream()
        .map(lot -> lot.units().multiply(lot.unitCost()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static LocalDate dayOf(java.time.Instant time) {
    return time.atZone(ESTONIAN_ZONE).toLocalDate();
  }
}
