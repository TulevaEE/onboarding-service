package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface FundValueProvider {

  Optional<FundValue> getValueForDate(String key, LocalDate date);

  Optional<FundValue> getLatestValue(String key, LocalDate date);

  default Optional<FundValue> getValueForDate(String key, LocalDate date, Instant updatedBefore) {
    return getValueForDate(key, date);
  }

  default Optional<FundValue> getLatestValue(String key, LocalDate date, Instant updatedBefore) {
    return getLatestValue(key, date);
  }
}
