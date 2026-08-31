package ee.tuleva.onboarding.fund;

import java.math.BigDecimal;
import java.time.Instant;

public interface SavingsFundUnitStats {

  BigDecimal unitsOutstanding();

  BigDecimal unitsOutstandingAt(Instant cutoff);

  int unitHolderCount();
}
