package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FundValueRepository extends FundValueQueries, FundValueWriter {
  List<FundValue> saveAll(List<FundValue> fundValues);

  List<FundValue> getGlobalStockValues();

  Map<String, LocalDate> findEarliestDates();

  Map<String, LocalDate> findLatestDateByKeys(Set<String> keys);

  List<FundValue> findLatestValuesByKeys(List<String> keys);

  List<FundValue> findValuesBetweenDatesForKeys(
      List<String> keys, LocalDate startDate, LocalDate endDate, int maxRows);
}
