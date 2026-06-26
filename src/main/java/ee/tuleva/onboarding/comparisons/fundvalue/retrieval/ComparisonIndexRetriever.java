package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface ComparisonIndexRetriever {
  String getKey();

  List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate);

  default boolean requiresWorkingDay() {
    return false;
  }

  // The storage keys this retriever writes. Single-series retrievers use getKey() (default).
  // Retrievers that fan out to many per-ticker storage keys must override this to enumerate all of
  // them, so the indexing job resumes from the least up-to-date key and fully backfills any key
  // that
  // has no data yet (e.g. a newly added ticker). Resuming from a provider-wide maximum date would
  // permanently skip both late-publishing tickers and brand-new ones.
  default Set<String> expectedStorageKeys() {
    return Set.of(getKey());
  }

  default Duration stalenessThreshold() {
    return Duration.ofDays(7);
  }
}
