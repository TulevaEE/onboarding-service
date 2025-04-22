package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerAccount(UUID accountId, List<LedgerEntry> entries, Instant createdTime) {

  public BigDecimal balance() {
    return entries.stream().map(LedgerEntry::amount).reduce(ZERO, BigDecimal::add);
  }
}
