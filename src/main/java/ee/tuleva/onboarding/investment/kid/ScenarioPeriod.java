package ee.tuleva.onboarding.investment.kid;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

record ScenarioPeriod(LocalDate start, LocalDate end) {

  private static final int ANNEX_IV_P6_PERIOD_YEARS = 10;
  private static final int CLOSE_STALE_AFTER_DAYS = 7;
  private static final double DAILY_SERIES_CLOSES_ON_AT_LEAST_THIS_SHARE_OF_A_MONTHS_WEEKDAYS = 0.5;

  static ScenarioPeriod endingWithLastCompleteMonth(LocalDate lastPriceDate, LocalDate today) {
    var lastMonthTheSeriesCloses = lastMonthTheSeriesCloses(lastPriceDate);
    var lastCalendarMonthEnded = YearMonth.from(today).minusMonths(1);
    var end =
        (lastMonthTheSeriesCloses.isBefore(lastCalendarMonthEnded)
                ? lastMonthTheSeriesCloses
                : lastCalendarMonthEnded)
            .atEndOfMonth();
    return new ScenarioPeriod(end.minusYears(ANNEX_IV_P6_PERIOD_YEARS), end);
  }

  private static YearMonth lastMonthTheSeriesCloses(LocalDate lastPriceDate) {
    var lastPriceMonth = YearMonth.from(lastPriceDate);
    return lastPriceDate.isBefore(lastWeekday(lastPriceMonth))
        ? lastPriceMonth.minusMonths(1)
        : lastPriceMonth;
  }

  private static LocalDate lastWeekday(YearMonth month) {
    return Stream.iterate(month.atEndOfMonth(), day -> day.minusDays(1))
        .filter(ScenarioPeriod::isWeekday)
        .findFirst()
        .orElseThrow();
  }

  LocalDate loadFrom() {
    return start.minusDays(CLOSE_STALE_AFTER_DAYS);
  }

  List<MonthEndPrice> monthEndPrices(List<FundValue> prices) {
    var closes = new TreeMap<LocalDate, BigDecimal>();
    prices.forEach(price -> closes.put(price.date(), price.value()));
    return monthEnds().stream().map(monthEnd -> monthEndPrice(closes, monthEnd)).toList();
  }

  List<FundValue> dailyCloses(List<FundValue> prices) {
    var closes =
        prices.stream()
            .filter(price -> !price.date().isBefore(start) && !price.date().isAfter(end))
            .filter(price -> isWeekday(price.date()))
            .sorted(comparing(FundValue::date))
            .toList();
    IntStream.range(1, closes.size())
        .filter(i -> isLongerGapThanAMarketClosure(closes.get(i - 1), closes.get(i)))
        .findFirst()
        .ifPresent(
            i -> {
              throw new KidCalculationRefusedException(
                  "Daily closes missing: after=%s, next=%s"
                      .formatted(closes.get(i - 1).date(), closes.get(i).date()));
            });
    completeMonths()
        .filter(
            month ->
                closesIn(closes, month)
                    < weekdaysIn(month)
                        * DAILY_SERIES_CLOSES_ON_AT_LEAST_THIS_SHARE_OF_A_MONTHS_WEEKDAYS)
        .findFirst()
        .ifPresent(
            month -> {
              throw new KidCalculationRefusedException(
                  "Daily closes too sparse for a daily series: month=%s, closes=%s, weekdays=%s"
                      .formatted(month, closesIn(closes, month), weekdaysIn(month)));
            });
    return closes;
  }

  private static boolean isLongerGapThanAMarketClosure(FundValue earlier, FundValue later) {
    return earlier.date().plusDays(CLOSE_STALE_AFTER_DAYS).isBefore(later.date());
  }

  private Stream<YearMonth> completeMonths() {
    return Stream.iterate(
            YearMonth.from(start),
            month -> !month.isAfter(YearMonth.from(end)),
            month -> month.plusMonths(1))
        .filter(month -> !month.atDay(1).isBefore(start) && !month.atEndOfMonth().isAfter(end));
  }

  private static long closesIn(List<FundValue> closes, YearMonth month) {
    return closes.stream().filter(close -> YearMonth.from(close.date()).equals(month)).count();
  }

  private static long weekdaysIn(YearMonth month) {
    return month
        .atDay(1)
        .datesUntil(month.atEndOfMonth().plusDays(1))
        .filter(ScenarioPeriod::isWeekday)
        .count();
  }

  private List<LocalDate> monthEnds() {
    return Stream.iterate(
            YearMonth.from(start),
            month -> !month.isAfter(YearMonth.from(end)),
            month -> month.plusMonths(1))
        .map(YearMonth::atEndOfMonth)
        .filter(monthEnd -> !monthEnd.isBefore(start))
        .toList();
  }

  private static MonthEndPrice monthEndPrice(
      NavigableMap<LocalDate, BigDecimal> closes, LocalDate monthEnd) {
    var close = closes.floorEntry(monthEnd);
    if (close == null || close.getKey().isBefore(monthEnd.minusDays(CLOSE_STALE_AFTER_DAYS))) {
      throw new KidCalculationRefusedException(
          "Month-end close missing or stale: monthEnd=%s, lastClose=%s"
              .formatted(monthEnd, close == null ? "none" : close.getKey()));
    }
    return new MonthEndPrice(monthEnd, close.getValue().doubleValue());
  }

  private static boolean isWeekday(LocalDate date) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
  }
}
