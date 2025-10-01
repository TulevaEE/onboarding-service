package ee.tuleva.onboarding.ledger;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerPartyRepository extends CrudRepository<LedgerParty, UUID> {
  LedgerParty findByOwnerId(String name);
}
