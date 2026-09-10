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

  @Query("select count(e) from LedgerEntry e where e.account.name = :accountName")
  long countEntriesForAccountName(@Param("accountName") String accountName);

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

  @Query(
      """
      select a.id from LedgerAccount a join a.entries e
      where a.owner is not null and a.accountType = ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY
      group by a.id having sum(e.amount) > 0
      """)
  List<UUID> findHolderAccountIdsInDebit();

  @Query(
      """
      select distinct p.id from LedgerTransaction p join p.entries pe
      where p.transactionType = ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_PAYOUT
        and pe.account.owner is not null
        and p.externalReference is not null
        and not exists (
          select 1 from LedgerTransaction r join r.entries re
          where r.transactionType = ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST
            and r.externalReference = p.externalReference
            and re.account = pe.account)
        and not exists (
          select 1 from LedgerTransaction c join c.entries ce
          where c.transactionType = ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT
            and c.externalReference = p.externalReference
            and ce.account = pe.account
            and ce.amount < 0)
      """)
  List<UUID> findPayoutIdsBookedToAnotherPartyThanPriced();
}
