package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Profile("dev")
@Repository
public interface LedgerAccountRepository extends CrudRepository<LedgerAccount, UUID> {
  LedgerAccount findByServiceAccountType(ServiceAccountType serviceAccountType);

  List<LedgerAccount> findAllByLedgerParty(LedgerParty ledgerParty);
}
