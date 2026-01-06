package ee.tuleva.onboarding.comparisons.fundvalue;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class FundValueFixture {
  public static final String DEFAULT_PROVIDER = "TEST";

  public static FundValue aFundValue(String key, LocalDate date, BigDecimal value) {
    return new FundValue(key, date, value, DEFAULT_PROVIDER, date.atStartOfDay(UTC).toInstant());
  }

  public static FundValue aFundValue(String key, LocalDate date, double value) {
    return aFundValue(key, date, BigDecimal.valueOf(value));
  }

  public static FundValue aFundValue(
      String key, LocalDate date, BigDecimal value, String provider) {
    return new FundValue(key, date, value, provider, date.atStartOfDay(UTC).toInstant());
  }

  public static FundValue aFundValue(
      String key, LocalDate date, BigDecimal value, String provider, Instant updatedAt) {
    return new FundValue(key, date, value, provider, updatedAt);
  }
}
