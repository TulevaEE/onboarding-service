package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.util.List;
import java.util.Optional;

public interface FundValueRepository {
  void saveAll(List<FundValue> fundValues);

  Optional<FundValue> findLastValueForFund(String fund);

  List<String> findAllKeys();
}
