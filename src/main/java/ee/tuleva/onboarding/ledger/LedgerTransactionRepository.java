package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

  boolean existsByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType);

  Optional<LedgerTransaction> findByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType);
}
