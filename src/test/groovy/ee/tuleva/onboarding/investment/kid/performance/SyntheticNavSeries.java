package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class SyntheticNavSeries {

  static final LocalDate LAST = LocalDate.of(2026, 10, 21);

  static List<FundValue> benchmark(String key) {
    var values = new ArrayList<FundValue>();
    long seed = 42;
    var price = new BigDecimal("100.00000");
    for (var day = LocalDate.of(2015, 1, 1); !day.isAfter(LAST); day = day.plusDays(1)) {
      if (isWeekend(day)) {
        continue;
      }
      seed = next(seed);
      price = price.multiply(BigDecimal.ONE.add(benchmarkReturn(seed, day))).setScale(5, HALF_UP);
      values.add(aFundValue(key, day, price));
    }
    return values;
  }

  static List<FundValue> nav(String key, LocalDate first, String startPrice, long seed) {
    return nav(key, first, startPrice, seed, 0);
  }

  static List<FundValue> nav(
      String key, LocalDate first, String startPrice, long seed, int skippedYear) {
    var values = new ArrayList<FundValue>();
    var price = new BigDecimal(startPrice);
    for (var day = first; !day.isAfter(LAST); day = day.plusDays(1)) {
      if (isWeekend(day)) {
        continue;
      }
      seed = next(seed);
      var dailyReturn =
          BigDecimal.valueOf(seed % 2001 - 1000)
              .divide(BigDecimal.valueOf(200000))
              .add(new BigDecimal("0.0003"));
      price = price.multiply(BigDecimal.ONE.add(dailyReturn)).setScale(5, HALF_UP);
      if (day.getYear() != skippedYear) {
        values.add(aFundValue(key, day, price));
      }
    }
    return values;
  }

  private static long next(long seed) {
    return (1103515245L * seed + 12345L) % 2147483648L;
  }

  private static BigDecimal benchmarkReturn(long seed, LocalDate day) {
    var dailyReturn =
        BigDecimal.valueOf(seed % 3001 - 1500)
            .divide(BigDecimal.valueOf(100000))
            .add(new BigDecimal("0.0008"));
    if (!day.isBefore(LocalDate.of(2020, 2, 20)) && !day.isAfter(LocalDate.of(2020, 3, 20))) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.012"));
    }
    if (day.getYear() == 2022) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.0008"));
    }
    if (!day.isBefore(LocalDate.of(2025, 10, 1)) && !day.isAfter(LocalDate.of(2026, 9, 30))) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.0006"));
    }
    return dailyReturn;
  }

  private static boolean isWeekend(LocalDate day) {
    return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
  }
}
