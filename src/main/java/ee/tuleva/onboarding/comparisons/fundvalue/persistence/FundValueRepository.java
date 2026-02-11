package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FundValueRepository {
  List<FundValue> saveAll(List<FundValue> fundValues);

  Optional<FundValue> save(FundValue fundValue);

  Optional<FundValue> findLastValueForFund(String fund);

  List<FundValue> getGlobalStockValues();

  Optional<LocalDate> findEarliestDateForKey(String key);

  Map<String, LocalDate> findEarliestDates();

  Optional<FundValue> getLatestValue(String key, LocalDate date);

  List<FundValue> findValuesBetweenDates(String fundKey, LocalDate startDate, LocalDate endDate);
}
