package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
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
      select sum(e.amount) from LedgerEntry e
      where e.transaction.externalReference = :externalReference
        and e.transaction.transactionType = :transactionType
        and e.assetType = :assetType
        and e.amount > 0
      """)
  Optional<BigDecimal> sumIncreasesOf(
      @Param("externalReference") UUID externalReference,
      @Param("transactionType") TransactionType transactionType,
      @Param("assetType") AssetType assetType);

  @Query(
      """
      select sum(e.amount) from LedgerEntry e
      where e.transaction.externalReference in :externalReferences
        and e.transaction.transactionType = :transactionType
        and e.assetType = :assetType
        and e.amount > 0
      """)
  Optional<BigDecimal> sumIncreasesOfAll(
      @Param("externalReferences") Collection<UUID> externalReferences,
      @Param("transactionType") TransactionType transactionType,
      @Param("assetType") AssetType assetType);

  @Query(
      """
      select coalesce(sum(e.amount), 0) from LedgerEntry e
      where e.transaction.transactionType = :transactionType
        and e.transaction.transactionDate > :after
        and e.transaction.transactionDate <= :until
        and e.assetType = :assetType
        and e.amount > 0
      """)
  BigDecimal sumIncreasesBetween(
      @Param("transactionType") TransactionType transactionType,
      @Param("after") Instant after,
      @Param("until") Instant until,
      @Param("assetType") AssetType assetType);

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
      where a.owner is not null and a.accountType = :accountType
      group by a.id having sum(e.amount) > 0
      """)
  List<UUID> findHolderAccountIdsInDebit(@Param("accountType") AccountType accountType);

  @Query(
      """
      select distinct p.id from LedgerTransaction p join p.entries pe
      where p.transactionType = :payout
        and pe.account.owner is not null
        and p.externalReference is not null
        and not exists (
          select 1 from LedgerTransaction r join r.entries re
          where r.transactionType = :pricing
            and r.externalReference = p.externalReference
            and re.account = pe.account)
        and not exists (
          select 1 from LedgerTransaction c join c.entries ce
          where c.transactionType = :adjustment
            and c.externalReference = p.externalReference
            and ce.account = pe.account
            and ce.amount < 0)
      """)
  List<UUID> findPayoutIdsBookedToAnotherPartyThanPriced(
      @Param("payout") TransactionType payout,
      @Param("pricing") TransactionType pricing,
      @Param("adjustment") TransactionType adjustment);
}
