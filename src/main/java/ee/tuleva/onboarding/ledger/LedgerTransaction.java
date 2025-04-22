package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerTransaction(UUID id, List<LedgerEntry> entries, Instant createdTime) {
  public LedgerTransaction {
    entries = List.copyOf(entries);
  }

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::amount).reduce(ZERO, BigDecimal::add);
  }
}
