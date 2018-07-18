package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

// TODO: delete this once we have implemented a fund value retriever
@Service
public class DummyEPIFundRetriever implements FundValueRetriever {
    @Override
    public ComparisonFund getRetrievalFund() {
        return ComparisonFund.EPI;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(Instant startDate, Instant endDate) {
        return Collections.emptyList();
    }
}
