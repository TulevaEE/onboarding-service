package ee.tuleva.onboarding.investment.kid.performance;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CalendarYearReturns {

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
    var yearEnds = new TreeMap<Integer, Double>();
    sorted.forEach(value -> yearEnds.put(value.date().getYear(), value.value().doubleValue()));

    var returns = new HashMap<Integer, CalendarYearReturn>();
    yearEnds.forEach(
        (year, yearEnd) -> {
          var previousYearEnd = yearEnds.get(year - 1);
          if (previousYearEnd != null) {
            returns.put(year, new CalendarYearReturn(yearEnd / previousYearEnd - 1, false));
          } else if (seriesStartsIn(sorted, seriesStart, year)) {
            var firstValue = sorted.getFirst().value().doubleValue();
            returns.put(year, new CalendarYearReturn(yearEnd / firstValue - 1, true));
          }
        });
    return Map.copyOf(returns);
  }

  private static boolean seriesStartsIn(List<FundValue> sorted, LocalDate seriesStart, int year) {
    return seriesStart.getYear() == year && sorted.getFirst().date().equals(seriesStart);
  }
}
