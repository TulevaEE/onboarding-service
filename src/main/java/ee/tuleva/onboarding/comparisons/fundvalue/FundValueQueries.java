package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface FundValueQueries extends FundValueProvider {

  Optional<FundValue> findLastValueForFund(String fund);

  Optional<LocalDate> findEarliestDateForKey(String key);

  List<FundValue> findValuesBetweenDates(String fundKey, LocalDate startDate, LocalDate endDate);
}
