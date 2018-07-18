package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;

import java.time.Instant;
import java.util.List;

public interface FundValueRetriever {
    ComparisonFund getRetrievalFund();
    List<FundValue> retrieveValuesForRange(Instant startDate, Instant endDate);
}
