package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface FundValueQueries extends FundValueProvider {

  Optional<FundValue> findLastValueForFund(String fund);

  Optional<LocalDate> findEarliestDateForKey(String key);
}
