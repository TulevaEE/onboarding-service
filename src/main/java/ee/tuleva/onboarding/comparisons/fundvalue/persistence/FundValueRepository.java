package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;

import java.util.List;
import java.util.Optional;

public interface FundValueRepository {
    List<FundValue> saveAll(List<FundValue> fundValues);
    Optional<FundValue> findLastValueForFund(ComparisonFund fund);
}
