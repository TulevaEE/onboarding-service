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
public class CostBasisCalculator {

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
        .filter(transaction -> transaction.units() != null && transaction.nav() != null)
        .filter(transaction -> !dayOf(transaction.time()).isAfter(to))
        .sorted(comparing(Transaction::time))
        .forEach(
            transaction -> {
              BigDecimal units = transaction.units().abs();

              if (transaction.isAcquisition()) {
                lots.add(new Lot(units, transaction.nav()));
                return;
              }

              Consumption consumption = consume(lots, units, method);
              lots.clear();
              lots.addAll(consumption.remainingLots());
              realised.add(toRealisedGain(transaction, units, consumption.acquisitionCost()));
            });

    return List.copyOf(realised);
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
