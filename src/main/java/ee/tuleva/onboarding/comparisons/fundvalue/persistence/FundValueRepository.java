package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FundValueRepository {
  void saveAll(List<FundValue> fundValues);

  void save(FundValue fundValue);

  void update(FundValue fundValue);

  Optional<FundValue> findExistingValueForFund(FundValue fundValue);

  Optional<FundValue> findLastValueForFund(String fund);

  List<FundValue> getGlobalStockValues();

  Optional<LocalDate> findEarliestDateForKey(String key);

  Map<String, LocalDate> findEarliestDates();

  List<FundValue> findValuesBetweenDates(String fundKey, LocalDate startDate, LocalDate endDate);
}
