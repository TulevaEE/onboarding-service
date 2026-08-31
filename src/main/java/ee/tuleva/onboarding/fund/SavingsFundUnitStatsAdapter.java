package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.ledger.SavingsFundUnits;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundUnitStatsAdapter implements SavingsFundUnitStats {

  private final SavingsFundUnits savingsFundUnits;

  @Override
  public BigDecimal unitsOutstanding() {
    return savingsFundUnits.unitsOutstanding();
  }

  @Override
  public BigDecimal unitsOutstandingAt(Instant cutoff) {
    return savingsFundUnits.unitsOutstandingAt(cutoff);
  }

  @Override
  public int unitHolderCount() {
    return savingsFundUnits.unitHolderCount();
  }
}
