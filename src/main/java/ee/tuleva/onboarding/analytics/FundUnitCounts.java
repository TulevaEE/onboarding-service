package ee.tuleva.onboarding.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FundUnitCounts {

  Optional<BigDecimal> totalUnitsAsOf(String isin, LocalDate date);
}
