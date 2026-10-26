package ee.tuleva.onboarding.investment.kid.performance;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class EquityCappedSeries {

  private static final int RESTART_BOND_CLOSE_AT_MOST_DAYS_OLD = 7;

  private final EquityCap cap;
  private final NavigableMap<LocalDate, FundValue> bondCloses;
  private final Optional<LocalDate> lift;
  private final Set<LocalDate> restarts;

  private EquityCappedSeries(
      EquityCap cap,
      NavigableMap<LocalDate, FundValue> bondCloses,
      Optional<LocalDate> lift,
      Set<LocalDate> restarts) {
    this.cap = cap;
    this.bondCloses = bondCloses;
    this.lift = lift;
    this.restarts = restarts;
  }

  static List<FundValue> of(
      List<FundValue> equity, List<FundValue> bond, EquityCap cap, LocalDate fundStart) {
    CalendarYearReturns.refuseANonPositiveValue(equity);
    CalendarYearReturns.refuseANonPositiveValue(bond);
    var bondCloses = new TreeMap<LocalDate, FundValue>();
    bond.forEach(close -> bondCloses.put(close.date(), close));
    var closes =
        equity.stream()
            .sorted(comparing(FundValue::date))
            .filter(
                close -> !cap.holdsOn(close.date()) || bondCloses.floorKey(close.date()) != null)
            .toList();
    var lift =
        closes.stream().anyMatch(close -> !cap.holdsOn(close.date()))
            ? lastOnOrBefore(closes, cap.liftedOn())
            : Optional.<LocalDate>empty();
    var series = new EquityCappedSeries(cap, bondCloses, lift, restarts(closes, fundStart, lift));
    return series.levels(closes.stream().map(series::day).toList());
  }

  private List<FundValue> levels(List<Day> days) {
    if (days.isEmpty()) {
      return List.of();
    }
    var equityWeight = cap.equityWeight().doubleValue();
    var levels = new ArrayList<FundValue>();
    var holding = new Holding(days.getFirst(), days.getFirst().equity().value().doubleValue());
    for (var day : days) {
      var level = holding.levelOn(day, equityWeight);
      levels.add(day.at(level));
      if (restarts.contains(day.date())) {
        holding = new Holding(day, level);
      }
    }
    return List.copyOf(levels);
  }

  private Day day(FundValue equityClose) {
    return cap.holdsOn(equityClose.date())
        ? new Day(equityClose, bondCloseFor(equityClose.date()))
        : new Day(equityClose, null);
  }

  private FundValue bondCloseFor(LocalDate day) {
    var valuedOn = lift.filter(day::equals).map(liftDay -> cap.liftedOn()).orElse(day);
    var bondClose = requireNonNull(bondCloses.floorEntry(valuedOn)).getValue();
    if (restarts.contains(day)
        && bondClose.date().isBefore(valuedOn.minusDays(RESTART_BOND_CLOSE_AT_MOST_DAYS_OLD))) {
      throw new PastPerformanceRefusedException(
          "Bond close too old on a day the equity-capped blend restarts: bondKey=%s, day=%s, bondClose=%s"
              .formatted(cap.bondKey(), valuedOn, bondClose.date()));
    }
    return bondClose;
  }

  private static Set<LocalDate> restarts(
      List<FundValue> closes, LocalDate fundStart, Optional<LocalDate> lift) {
    var yearEnds =
        closes.stream()
            .collect(toMap(close -> close.date().getYear(), FundValue::date, maxBy(naturalOrder())))
            .values()
            .stream();
    var fundStartAndLift =
        Stream.of(lastOnOrBefore(closes, fundStart), lift).flatMap(Optional::stream);
    return Stream.concat(yearEnds, fundStartAndLift).collect(toSet());
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

  private record Holding(Day start, double level) {

    double levelOn(Day day, double equityWeight) {
      var equityGrowth = ratio(day.equity(), start.equity());
      var startBond = start.bond();
      var dayBond = day.bond();
      return startBond == null || dayBond == null
          ? level * equityGrowth
          : level * (equityWeight * equityGrowth + (1 - equityWeight) * ratio(dayBond, startBond));
    }
  }
}
