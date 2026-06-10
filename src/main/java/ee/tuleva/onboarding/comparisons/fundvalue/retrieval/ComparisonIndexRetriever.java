package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ComparisonIndexRetriever {
  String getKey();

  List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate);

  default boolean requiresWorkingDay() {
    return false;
  }

  // Retrievers that store one value series per getKey() track incremental progress by key
  // (default).
  // Retrievers that fan out to many per-ticker storage keys under a single provider must override
  // this to return that provider, so the indexing job resumes from the provider's least up-to-date
  // key. Resuming from the provider-wide maximum would permanently skip tickers whose sources
  // publish values a few days late (e.g. mutual fund NAVs vs exchange-traded closing prices).
  default Optional<String> trackingProvider() {
    return Optional.empty();
  }

  default Duration stalenessThreshold() {
    return Duration.ofDays(7);
  }
}
