package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class SyntheticIndexSeries {

  private static final LocalDate FIRST = LocalDate.of(2015, 1, 1);
  private static final LocalDate LAST = LocalDate.of(2026, 10, 21);
  private static final LocalDate CRASH_FROM = LocalDate.of(2020, 2, 20);
  private static final LocalDate CRASH_TO = LocalDate.of(2020, 3, 20);
  private static final int BEAR_YEAR = 2022;
  private static final LocalDate WEAK_FROM = LocalDate.of(2025, 10, 1);
  private static final LocalDate WEAK_TO = LocalDate.of(2026, 9, 30);

  static List<FundValue> weekdayCloses(String key) {
    var values = new ArrayList<FundValue>();
    long seed = 42;
    var price = new BigDecimal("100.00000");
    for (var day = FIRST; !day.isAfter(LAST); day = day.plusDays(1)) {
      if (isWeekend(day)) {
        continue;
      }
      seed = (1103515245L * seed + 12345L) % 2147483648L;
      price = price.multiply(BigDecimal.ONE.add(dailyReturn(seed, day))).setScale(5, HALF_UP);
      values.add(aFundValue(key, day, price));
    }
    return values;
  }

  private static BigDecimal dailyReturn(long seed, LocalDate day) {
    var dailyReturn =
        BigDecimal.valueOf(seed % 3001 - 1500)
            .divide(BigDecimal.valueOf(100000))
            .add(new BigDecimal("0.0008"));
    if (!day.isBefore(CRASH_FROM) && !day.isAfter(CRASH_TO)) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.012"));
    }
    if (day.getYear() == BEAR_YEAR) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.0008"));
    }
    if (!day.isBefore(WEAK_FROM) && !day.isAfter(WEAK_TO)) {
      dailyReturn = dailyReturn.subtract(new BigDecimal("0.0006"));
    }
    return dailyReturn;
  }

  private static boolean isWeekend(LocalDate day) {
    return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
  }
}
