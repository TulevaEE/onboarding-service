package ee.tuleva.onboarding.investment.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface InstrumentRetirementThreshold {

  Optional<BigDecimal> requiredNavDatesOffTheBooks(LocalDate asOf);
}
