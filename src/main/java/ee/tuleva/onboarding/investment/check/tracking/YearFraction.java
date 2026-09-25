package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

final class YearFraction {

  private static final MathContext PRECISION = new MathContext(20, HALF_UP);

  private YearFraction() {}

  static BigDecimal eachDayWeighedByItsOwnYear(LocalDate firstDay, LocalDate lastDay) {
    return IntStream.rangeClosed(firstDay.getYear(), lastDay.getYear())
        .mapToObj(Year::of)
        .map(
            year ->
                shareOf(year, later(firstDay, year.atDay(1)), earlier(lastDay, lastDayOf(year))))
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal shareOf(Year year, LocalDate from, LocalDate to) {
    var days = Math.max(0, ChronoUnit.DAYS.between(from, to) + 1);
    return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(year.length()), PRECISION);
  }

  private static LocalDate lastDayOf(Year year) {
    return year.atDay(year.length());
  }

  private static LocalDate later(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate earlier(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }
}
