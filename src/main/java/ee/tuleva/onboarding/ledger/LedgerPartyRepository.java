package ee.tuleva.onboarding.ledger;

import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Profile("dev")
@Repository
public interface LedgerPartyRepository extends CrudRepository<LedgerParty, UUID> {
  LedgerParty findByName(String name);
}
