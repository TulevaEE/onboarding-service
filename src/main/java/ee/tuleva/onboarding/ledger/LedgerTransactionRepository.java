package ee.tuleva.onboarding.ledger;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerTransactionRepository extends CrudRepository<LedgerTransaction, UUID> {

  boolean existsByExternalReference(UUID externalReference);
}
