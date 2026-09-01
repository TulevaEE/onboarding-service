package ee.tuleva.onboarding.comparisons.fundvalue;

import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface FundValueWriter {

  Optional<FundValue> save(FundValue fundValue);
}
