package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


// TODO: delete this once we have implemented a fund value repository
@Repository
public class DummyFundValueRepository implements FundValueRepository {
    @Override
    public List<FundValue> saveAll(List<FundValue> fundValues) {
        return fundValues;
    }

    @Override
    public Optional<FundValue> findLastValueForFund(ComparisonFund fund) {
        return Optional.empty();
    }
}
