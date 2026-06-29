package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.util.Optional;
import java.util.UUID;
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
              insertPartyIfAbsent(ownerId, partyType);
              return getParty(ownerId, partyType).orElseThrow();
            });
  }

  public Optional<LedgerParty> getParty(String ownerId, PartyType partyType) {
    return Optional.ofNullable(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, partyType));
  }

  void insertPartyIfAbsent(String ownerId, PartyType partyType) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.party (id, party_type, owner_id, details)
            VALUES (:id, CAST(:partyType AS ledger.party_type), :ownerId, '{}')
            ON CONFLICT DO NOTHING
            """)
        .param("id", UUID.randomUUID())
        .param("partyType", partyType.name())
        .param("ownerId", ownerId)
        .update();
  }
}
