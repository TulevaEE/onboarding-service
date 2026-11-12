package ee.tuleva.onboarding.comparisons.fundvalue;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class EquityCappedSeries {

  private static final int RESTART_CLOSE_AT_MOST_DAYS_OLD = 7;
  private static final double EQUITY_ONLY = 1.0;

  private final List<EquityCap> caps;
  private final NavigableMap<LocalDate, FundValue> bondCloses;
  private final Map<LocalDate, EquityCap> lifts;
  private final Set<LocalDate> restarts;

  private EquityCappedSeries(
      List<EquityCap> caps,
      NavigableMap<LocalDate, FundValue> bondCloses,
      Map<LocalDate, EquityCap> lifts,
      Set<LocalDate> restarts) {
    this.caps = caps;
    this.bondCloses = bondCloses;
    this.lifts = lifts;
    this.restarts = restarts;
  }

  public static List<FundValue> of(
      List<FundValue> equity, List<FundValue> bond, List<EquityCap> caps, LocalDate fundStart) {
    var capsInForceOrder = caps.stream().sorted(comparing(EquityCap::liftedOn)).toList();
    refuseANonPositiveValue(equity);
    refuseANonPositiveValue(bond);
    refuseAMissingBondUnderACap(equity, bond, capsInForceOrder);
    var bondCloses = new TreeMap<LocalDate, FundValue>();
    bond.forEach(close -> bondCloses.put(close.date(), close));
    var closes =
        equity.stream()
            .sorted(comparing(FundValue::date))
            .filter(
                close ->
                    capOn(capsInForceOrder, close.date()).isEmpty()
                        || bondCloses.floorKey(close.date()) != null)
            .toList();
    var lifts = lifts(closes, capsInForceOrder);
    var series =
        new EquityCappedSeries(
            capsInForceOrder, bondCloses, lifts, restarts(closes, fundStart, lifts.keySet()));
    return series.levels(closes.stream().map(series::day).toList());
  }

  private static Map<LocalDate, EquityCap> lifts(List<FundValue> closes, List<EquityCap> caps) {
    var lifts = new HashMap<LocalDate, EquityCap>();
    caps.stream()
        .filter(cap -> closes.stream().anyMatch(close -> !cap.holdsOn(close.date())))
        .forEach(
            cap ->
                lastOnOrBefore(closes, cap.liftedOn())
                    .ifPresent(
                        lift -> {
                          refuseAnEquityCloseTooOldAtTheLift(cap, lift);
                          lifts.put(lift, cap);
                        }));
    return Map.copyOf(lifts);
  }

  private static void refuseAnEquityCloseTooOldAtTheLift(EquityCap cap, LocalDate lift) {
    if (lift.isBefore(cap.liftedOn().minusDays(RESTART_CLOSE_AT_MOST_DAYS_OLD))) {
      throw new EquityCappedSeriesRefusedException(
          "Equity close too old at the equity cap's lift: liftedOn=%s, equityClose=%s"
              .formatted(cap.liftedOn(), lift));
    }
  }

  private static void refuseANonPositiveValue(List<FundValue> values) {
    values.stream()
        .filter(value -> value.value().signum() <= 0)
        .min(comparing(FundValue::date))
        .ifPresent(
            value -> {
              throw new EquityCappedSeriesRefusedException(
                  "Value not positive: key=%s, date=%s".formatted(value.key(), value.date()));
            });
  }

  private static void refuseAMissingBondUnderACap(
      List<FundValue> equity, List<FundValue> bond, List<EquityCap> caps) {
    if (!bond.isEmpty()) {
      return;
    }
    equity.stream()
        .map(close -> capOn(caps, close.date()))
        .flatMap(Optional::stream)
        .findFirst()
        .ifPresent(
            cap -> {
              throw new EquityCappedSeriesRefusedException(
                  "No bond series for the equity cap: bondKey=%s".formatted(cap.bondKey()));
            });
  }

  private static Optional<EquityCap> capOn(List<EquityCap> capsInForceOrder, LocalDate date) {
    return capsInForceOrder.stream().filter(cap -> cap.holdsOn(date)).findFirst();
  }

  private List<FundValue> levels(List<Day> days) {
    if (days.isEmpty()) {
      return List.of();
    }
    var levels = new ArrayList<FundValue>();
    var holding =
        new Holding(
            days.getFirst(), days.getFirst().equity().value().doubleValue(), weightAfter(days, 0));
    for (var index = 0; index < days.size(); index++) {
      var day = days.get(index);
      var level = holding.levelOn(day);
      levels.add(day.at(level));
      if (restarts.contains(day.date())) {
        holding = new Holding(day, level, weightAfter(days, index));
      }
    }
    return List.copyOf(levels);
  }

  private double weightAfter(List<Day> days, int index) {
    return index + 1 < days.size()
        ? capOn(caps, days.get(index + 1).date())
            .map(cap -> cap.equityWeight().doubleValue())
            .orElse(EQUITY_ONLY)
        : EQUITY_ONLY;
  }

  private Day day(FundValue equityClose) {
    return capOn(caps, equityClose.date())
        .map(cap -> new Day(equityClose, bondCloseFor(equityClose.date(), cap)))
        .orElseGet(() -> new Day(equityClose, null));
  }

  private FundValue bondCloseFor(LocalDate day, EquityCap cap) {
    var liftedThatDay = lifts.get(day);
    var valuedOn = liftedThatDay == null ? day : liftedThatDay.liftedOn();
    var bondClose = requireNonNull(bondCloses.floorEntry(valuedOn)).getValue();
    if (restarts.contains(day)
        && bondClose.date().isBefore(valuedOn.minusDays(RESTART_CLOSE_AT_MOST_DAYS_OLD))) {
      throw new EquityCappedSeriesRefusedException(
          "Bond close too old on a day the equity-capped blend restarts: bondKey=%s, day=%s, valuedOn=%s, bondClose=%s"
              .formatted(cap.bondKey(), day, valuedOn, bondClose.date()));
    }
    return bondClose;
  }

  private static Set<LocalDate> restarts(
      List<FundValue> closes, LocalDate fundStart, Set<LocalDate> liftDays) {
    var yearEnds =
        closes.stream()
            .collect(toMap(close -> close.date().getYear(), FundValue::date, maxBy(naturalOrder())))
            .values()
            .stream();
    var fundStartAndLifts =
        Stream.concat(lastOnOrBefore(closes, fundStart).stream(), liftDays.stream());
    return Stream.concat(yearEnds, fundStartAndLifts).collect(toSet());
  }

  private static Optional<LocalDate> lastOnOrBefore(List<FundValue> closes, LocalDate date) {
    return closes.stream()
        .map(FundValue::date)
        .filter(day -> !day.isAfter(date))
        .max(naturalOrder());
  }

  private static double ratio(FundValue to, FundValue from) {
    return to.value().doubleValue() / from.value().doubleValue();
  }

  private record Day(FundValue equity, @Nullable FundValue bond) {

    LocalDate date() {
      return equity.date();
    }

    FundValue at(double level) {
      return new FundValue(
          equity.key(),
          equity.date(),
          BigDecimal.valueOf(level),
          equity.provider(),
          equity.updatedAt());
    }
  }

  private record Holding(Day start, double level, double equityWeight) {

    double levelOn(Day day) {
      var equityGrowth = ratio(day.equity(), start.equity());
      var startBond = start.bond();
      var dayBond = day.bond();
      return startBond == null || dayBond == null
          ? level * equityGrowth
          : level * (equityWeight * equityGrowth + (1 - equityWeight) * ratio(dayBond, startBond));
    }
  }
}
