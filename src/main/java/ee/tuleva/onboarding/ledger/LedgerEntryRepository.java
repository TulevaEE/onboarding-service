package ee.tuleva.onboarding.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Profile({"dev", "test"})
@Repository
public interface LedgerEntryRepository extends CrudRepository<LedgerEntry, UUID> {
  List<LedgerEntry> findByAccount(LedgerAccount account);

  List<LedgerEntry> findByTransaction(LedgerTransaction account);
}
