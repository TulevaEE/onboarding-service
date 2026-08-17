package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

  boolean existsByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType);

  boolean existsByExternalReference(UUID externalReference);

  Optional<LedgerTransaction> findByExternalReferenceAndTransactionType(
      UUID externalReference, TransactionType transactionType);

  @Query(
      """
      select count(distinct t.id) from LedgerTransaction t join t.entries e
      where t.transactionType = :transactionType
        and e.account.name = :accountName
        and not exists (
          select 1 from LedgerTransaction r
          where r.externalReference = t.externalReference
            and r.transactionType <> :transactionType)
      """)
  long countUnresolvedByTransactionTypeAndAccountName(
      @Param("transactionType") TransactionType transactionType,
      @Param("accountName") String accountName);

  @Query(
      """
      select distinct t from LedgerTransaction t join t.entries e
      where t.transactionType = :transactionType
        and e.account.name = :accountName
        and not exists (
          select 1 from LedgerTransaction r
          where r.externalReference = t.externalReference
            and r.transactionType <> :transactionType)
      """)
  List<LedgerTransaction> findUnresolvedByTransactionTypeAndAccountName(
      @Param("transactionType") TransactionType transactionType,
      @Param("accountName") String accountName);
}
