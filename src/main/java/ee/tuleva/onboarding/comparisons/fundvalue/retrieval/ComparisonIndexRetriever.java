package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public interface ComparisonIndexRetriever {
  String getKey();

  List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate);

  default boolean requiresWorkingDay() {
    return false;
  }

  default Duration stalenessThreshold() {
    return Duration.ofDays(7);
  }
}
