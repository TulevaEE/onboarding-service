package ee.tuleva.onboarding.investment.kid.performance;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

final class CalendarYearReturns {

  private static final int CLOSE_STALE_AFTER_DAYS = 7;

  private CalendarYearReturns() {}

  static Map<Integer, CalendarYearReturn> of(List<FundValue> values, LocalDate seriesStart) {
    var sorted = values.stream().sorted(comparing(FundValue::date)).toList();
    sorted.stream()
        .filter(value -> value.value().signum() <= 0)
        .findFirst()
        .ifPresent(
            value -> {
              throw new PastPerformanceRefusedException(
                  "Value not positive: key=%s, date=%s".formatted(value.key(), value.date()));
            });
    var yearEnds = new TreeMap<Integer, FundValue>();
    sorted.forEach(value -> yearEnds.put(value.date().getYear(), value));
    if (yearEnds.isEmpty()) {
      return Map.of();
    }
    int openYear = yearEnds.lastKey();

    var returns = new HashMap<Integer, CalendarYearReturn>();
    yearEnds.forEach(
        (year, yearEnd) -> {
          if (year != openYear && !endsItsYear(yearEnd)) {
            return;
          }
          var previousYearEnd = yearEnds.get(year - 1);
          if (previousYearEnd != null && endsItsYear(previousYearEnd)) {
            returns.put(year, new CalendarYearReturn(growth(previousYearEnd, yearEnd), false));
          } else if (seriesStartsIn(sorted, seriesStart, year)) {
            returns.put(year, new CalendarYearReturn(growth(sorted.getFirst(), yearEnd), true));
          }
        });
    return Map.copyOf(returns);
  }

  static OptionalDouble growthSince(List<FundValue> values, LocalDate from, int year) {
    var sorted = values.stream().sorted(comparing(FundValue::date)).toList();
    var base =
        sorted.stream()
            .filter(value -> !value.date().isAfter(from))
            .reduce((earlier, later) -> later)
            .filter(value -> !value.date().isBefore(from.minusDays(CLOSE_STALE_AFTER_DAYS)));
    var yearEnd =
        sorted.stream()
            .filter(value -> value.date().getYear() == year)
            .reduce((earlier, later) -> later)
            .filter(value -> year == sorted.getLast().date().getYear() || endsItsYear(value));
    return base.isPresent() && yearEnd.isPresent()
        ? OptionalDouble.of(growth(base.get(), yearEnd.get()))
        : OptionalDouble.empty();
  }

  private static boolean endsItsYear(FundValue close) {
    var thirtyFirst = LocalDate.of(close.date().getYear(), 12, 31);
    return !close.date().isBefore(thirtyFirst.minusDays(CLOSE_STALE_AFTER_DAYS));
  }

  private static double growth(FundValue from, FundValue to) {
    return to.value().doubleValue() / from.value().doubleValue() - 1;
  }

  private static boolean seriesStartsIn(List<FundValue> sorted, LocalDate seriesStart, int year) {
    return seriesStart.getYear() == year && sorted.getFirst().date().equals(seriesStart);
  }
}
