package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.LocalDate;
import java.util.Optional;

public interface FundValueProvider {
    Optional<FundValue> getLatestValue(String comparisonFund, LocalDate date);
}
