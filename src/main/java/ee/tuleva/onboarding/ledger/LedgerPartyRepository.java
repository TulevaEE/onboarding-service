package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerPartyRepository extends CrudRepository<LedgerParty, UUID> {
  LedgerParty findByOwnerIdAndPartyType(String ownerId, PartyType partyType);
}
