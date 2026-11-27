package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;

import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundUnits;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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

  @Override
  public Optional<BigDecimal> unitsHeldAt(String registryCode, Instant cutoff) {
    return savingsFundUnits.unitsHeldAt(new PartyRef(LEGAL_ENTITY, registryCode), cutoff);
  }
}
