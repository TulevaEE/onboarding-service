package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerPartyService {

  private final LedgerPartyRepository ledgerPartyRepository;
  private final JdbcClient jdbcClient;

  LedgerParty getOrCreate(String ownerId, PartyType partyType) {
    return getParty(ownerId, partyType)
        .orElseGet(
            () -> {
              acquirePartyLock(partyType, ownerId);
              return getParty(ownerId, partyType).orElseGet(() -> createParty(ownerId, partyType));
            });
  }

  LedgerParty createParty(String ownerId, PartyType partyType) {
    var ledgerParty =
        LedgerParty.builder().partyType(partyType).ownerId(ownerId).details(Map.of()).build();

    return ledgerPartyRepository.save(ledgerParty);
  }

  public Optional<LedgerParty> getParty(String ownerId, PartyType partyType) {
    return Optional.ofNullable(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, partyType));
  }

  private void acquirePartyLock(PartyType partyType, String ownerId) {
    jdbcClient
        .sql("SELECT pg_advisory_xact_lock(:key)")
        .param("key", (long) (partyType.name() + ":" + ownerId).hashCode())
        .query(Long.class)
        .optional();
  }
}
