package ee.tuleva.onboarding.fund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface SavingsFundUnitStats {

  BigDecimal unitsOutstanding();

  BigDecimal unitsOutstandingAt(Instant cutoff);

  int unitHolderCount();

  Optional<BigDecimal> unitsHeldAt(String registryCode, Instant cutoff);
}
