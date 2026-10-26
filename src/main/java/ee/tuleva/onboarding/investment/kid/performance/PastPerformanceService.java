package ee.tuleva.onboarding.investment.kid.performance;

import static java.math.RoundingMode.HALF_UP;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PastPerformanceService {

  private static final int CALENDAR_YEARS_SHOWN = 10;
  private static final int RETURN_SCALE = 8;

  private final PastPerformanceProperties properties;
  private final FundValueQueries fundValueQueries;
  private final Clock clock;

  PastPerformance pastPerformance(TulevaFund fund) {
    var fundKey = fund.getIsin();
    var benchmarkKey = properties.benchmarkFor(fund);
    var today = LocalDate.now(clock);
    var asOf = asOf(fundKey, benchmarkKey, today);
    var openYear = openYear(asOf);
    var firstYearShown = openYear - CALENDAR_YEARS_SHOWN;

    var fundStart = seriesStart(fundKey);
    var fundReturns =
        CalendarYearReturns.of(
            fundValues(fundKey, benchmarkKey, firstYearShown, asOf), fundStart, openYear);
    var benchmarkValues =
        benchmarkKey == null
            ? List.<FundValue>of()
            : benchmarkValues(fund, benchmarkKey, firstYearShown, asOf, fundStart);
    var benchmarkReturns =
        benchmarkKey == null
            ? Map.<Integer, CalendarYearReturn>of()
            : CalendarYearReturns.of(benchmarkValues, seriesStart(benchmarkKey), openYear);

    var years =
        fundReturns.entrySet().stream()
            .filter(year -> year.getKey() >= firstYearShown)
            .sorted(Map.Entry.comparingByKey())
            .map(
                year ->
                    new YearReturn(
                        year.getKey(),
                        rate(year.getValue().value()),
                        year.getValue().partialYear()
                            ? benchmarkSinceTheFundStarted(
                                benchmarkValues, fundStart, year.getKey(), openYear)
                            : benchmarkReturn(benchmarkReturns.get(year.getKey())),
                        year.getValue().partialYear(),
                        year.getKey() == openYear))
            .toList();
    return new PastPerformance(
        fund,
        fundKey,
        benchmarkKey,
        equityCapHeldInAYearShown(fund, firstYearShown, fundStart),
        asOf,
        years.stream().anyMatch(year -> !year.partialYear() && !year.yearToDate()),
        years);
  }

  private LocalDate asOf(String fundKey, @Nullable String benchmarkKey, LocalDate today) {
    var fundLast = lastDate(fundKey);
    var benchmarkLast = benchmarkKey == null ? fundLast : lastDate(benchmarkKey);
    return Stream.of(fundLast, benchmarkLast, today).min(LocalDate::compareTo).orElseThrow();
  }

  private static int openYear(LocalDate asOf) {
    return asOf.isBefore(lastWeekdayOf(asOf.getYear())) ? asOf.getYear() : asOf.getYear() + 1;
  }

  private static LocalDate lastWeekdayOf(int year) {
    return Stream.iterate(LocalDate.of(year, 12, 31), day -> day.minusDays(1))
        .filter(day -> day.getDayOfWeek() != SATURDAY && day.getDayOfWeek() != SUNDAY)
        .findFirst()
        .orElseThrow();
  }

  private LocalDate lastDate(String key) {
    return fundValueQueries
        .findLastValueForFund(key)
        .map(FundValue::date)
        .orElseThrow(
            () -> new PastPerformanceRefusedException("No values in the series: key=" + key));
  }

  private List<FundValue> fundValues(
      String fundKey, @Nullable String benchmarkKey, int firstYearShown, LocalDate asOf) {
    var values = values(fundKey, firstYearShown, asOf);
    if (values.isEmpty()) {
      throw new PastPerformanceRefusedException(
          "Benchmark ends before the fund's first value: fundKey=%s, benchmarkKey=%s, asOf=%s"
              .formatted(fundKey, benchmarkKey, asOf));
    }
    return values;
  }

  private @Nullable EquityCap equityCapHeldInAYearShown(
      TulevaFund fund, int firstYearShown, LocalDate fundStart) {
    var equityCap = properties.equityCapFor(fund);
    return equityCap != null && equityCap.heldInAYearFrom(firstYearShown, fundStart)
        ? equityCap
        : null;
  }

  private List<FundValue> benchmarkValues(
      TulevaFund fund,
      String benchmarkKey,
      int firstYearShown,
      LocalDate asOf,
      LocalDate fundStart) {
    var values = values(benchmarkKey, firstYearShown, asOf);
    var equityCap = properties.equityCapFor(fund);
    return equityCap == null
        ? values
        : EquityCappedSeries.of(
            values, values(equityCap.bondKey(), firstYearShown, asOf), equityCap, fundStart);
  }

  private List<FundValue> values(String key, int firstYearShown, LocalDate asOf) {
    return fundValueQueries.findValuesBetweenDates(
        key, LocalDate.of(firstYearShown - 1, 1, 1), asOf);
  }

  private LocalDate seriesStart(String key) {
    return fundValueQueries.findEarliestDateForKey(key).orElseThrow();
  }

  private static @Nullable BigDecimal benchmarkSinceTheFundStarted(
      List<FundValue> benchmark, LocalDate fundStart, int year, int openYear) {
    var growth = CalendarYearReturns.growthSince(benchmark, fundStart, year, openYear);
    return growth.isPresent() ? rate(growth.getAsDouble()) : null;
  }

  private static @Nullable BigDecimal benchmarkReturn(@Nullable CalendarYearReturn benchmark) {
    return benchmark == null || benchmark.partialYear() ? null : rate(benchmark.value());
  }

  private static BigDecimal rate(double value) {
    return BigDecimal.valueOf(value).setScale(RETURN_SCALE, HALF_UP);
  }
}
