package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

  boolean existsByExternalReference(UUID externalReference);

  boolean existsByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType);
}
