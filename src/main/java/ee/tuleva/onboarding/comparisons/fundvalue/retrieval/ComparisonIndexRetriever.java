package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;

import java.time.LocalDate;
import java.util.List;

public interface ComparisonIndexRetriever {
    String getKey();
    List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate);
}
