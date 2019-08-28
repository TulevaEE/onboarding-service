package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.Instant;
import java.util.Optional;

public interface FundValueProvider {
    Optional<FundValue> getLatestValue(String comparisonFund, Instant time);
}
