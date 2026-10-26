package ee.tuleva.onboarding.investment.kid.performance;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
    var firstYearShown = asOf.getYear() - CALENDAR_YEARS_SHOWN;

    var fundReturns = calendarYearReturns(fundKey, firstYearShown, asOf);
    var benchmarkReturns =
        benchmarkKey == null
            ? Map.<Integer, CalendarYearReturn>of()
            : calendarYearReturns(benchmarkKey, firstYearShown, asOf);

    var years =
        fundReturns.entrySet().stream()
            .filter(year -> year.getKey() >= firstYearShown)
            .sorted(Map.Entry.comparingByKey())
            .map(
                year ->
                    new YearReturn(
                        year.getKey(),
                        rate(year.getValue()),
                        benchmarkReturn(benchmarkReturns.get(year.getKey())),
                        year.getValue().partialYear(),
                        year.getKey() == asOf.getYear()))
            .toList();
    return new PastPerformance(
        fund,
        fundKey,
        benchmarkKey,
        asOf,
        years.stream().anyMatch(year -> !year.partialYear() && !year.yearToDate()),
        years);
  }

  private LocalDate asOf(String fundKey, @Nullable String benchmarkKey, LocalDate today) {
    var fundLast = lastDate(fundKey);
    var benchmarkLast = benchmarkKey == null ? fundLast : lastDate(benchmarkKey);
    return Stream.of(fundLast, benchmarkLast, today).min(LocalDate::compareTo).orElseThrow();
  }

  private LocalDate lastDate(String key) {
    return fundValueQueries
        .findLastValueForFund(key)
        .map(FundValue::date)
        .orElseThrow(
            () -> new PastPerformanceRefusedException("No values in the series: key=" + key));
  }

  private Map<Integer, CalendarYearReturn> calendarYearReturns(
      String key, int firstYearShown, LocalDate asOf) {
    var seriesStart = fundValueQueries.findEarliestDateForKey(key).orElseThrow();
    var values =
        fundValueQueries.findValuesBetweenDates(key, LocalDate.of(firstYearShown - 1, 1, 1), asOf);
    return CalendarYearReturns.of(values, seriesStart);
  }

  private static @Nullable BigDecimal benchmarkReturn(@Nullable CalendarYearReturn benchmark) {
    return benchmark == null || benchmark.partialYear() ? null : rate(benchmark);
  }

  private static BigDecimal rate(CalendarYearReturn calendarYearReturn) {
    return BigDecimal.valueOf(calendarYearReturn.value()).setScale(RETURN_SCALE, HALF_UP);
  }
}
