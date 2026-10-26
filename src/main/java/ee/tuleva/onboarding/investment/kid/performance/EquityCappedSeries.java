package ee.tuleva.onboarding.investment.kid.performance;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
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

  private static final int BOND_CLOSE_AT_MOST_DAYS_OLD = 7;

  private EquityCappedSeries() {}

  static List<FundValue> of(
      List<FundValue> equity, List<FundValue> bond, EquityCap cap, LocalDate fundStart) {
    CalendarYearReturns.refuseANonPositiveValue(equity);
    CalendarYearReturns.refuseANonPositiveValue(bond);
    var bondCloses = new TreeMap<LocalDate, FundValue>();
    bond.forEach(close -> bondCloses.put(close.date(), close));
    var days =
        equity.stream()
            .sorted(comparing(FundValue::date))
            .map(
                close ->
                    new Day(
                        close,
                        cap.holdsOn(close.date()) ? bondCloseOn(bondCloses, close.date()) : null))
            .filter(day -> !cap.holdsOn(day.date()) || day.bond() != null)
            .toList();
    refuseABondThatStopsBeforeTheLastCloseUnderTheCap(equity, days, cap);
    var restarts = restarts(days, fundStart, cap);
    var equityWeight = cap.equityWeight().doubleValue();

    var levels = new ArrayList<FundValue>();
    Holding holding = null;
    for (var day : days) {
      var level =
          holding == null ? day.equity().value().doubleValue() : holding.levelOn(day, equityWeight);
      levels.add(day.at(level));
      if (holding == null || restarts.contains(day.date())) {
        holding = new Holding(day, level);
      }
    }
    return List.copyOf(levels);
  }

  private static @Nullable FundValue bondCloseOn(
      NavigableMap<LocalDate, FundValue> bondCloses, LocalDate date) {
    var close = bondCloses.floorEntry(date);
    return close == null || close.getKey().isBefore(date.minusDays(BOND_CLOSE_AT_MOST_DAYS_OLD))
        ? null
        : close.getValue();
  }

  private static void refuseABondThatStopsBeforeTheLastCloseUnderTheCap(
      List<FundValue> equity, List<Day> days, EquityCap cap) {
    var lastEquityCloseUnderTheCap =
        equity.stream().map(FundValue::date).filter(cap::holdsOn).max(naturalOrder());
    var lastDayUnderTheCap = lastOnOrBefore(days, cap.liftedOn());
    if (!lastEquityCloseUnderTheCap.equals(lastDayUnderTheCap)) {
      throw new PastPerformanceRefusedException(
          "Bond closes stop before the last close under the equity cap: bondKey=%s, liftedOn=%s, lastClose=%s"
              .formatted(cap.bondKey(), cap.liftedOn(), lastEquityCloseUnderTheCap.orElseThrow()));
    }
  }

  private static Set<LocalDate> restarts(List<Day> days, LocalDate fundStart, EquityCap cap) {
    var yearEnds =
        days.stream()
            .collect(toMap(day -> day.date().getYear(), Day::date, maxBy(naturalOrder())))
            .values()
            .stream();
    var fundStartAndLift =
        Stream.of(lastOnOrBefore(days, fundStart), lastOnOrBefore(days, cap.liftedOn()))
            .flatMap(Optional::stream);
    return Stream.concat(yearEnds, fundStartAndLift).collect(toSet());
  }

  private static Optional<LocalDate> lastOnOrBefore(List<Day> days, LocalDate date) {
    return days.stream().map(Day::date).filter(day -> !day.isAfter(date)).max(naturalOrder());
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
