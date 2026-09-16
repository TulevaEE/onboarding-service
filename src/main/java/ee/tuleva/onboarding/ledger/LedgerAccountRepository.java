package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerAccountRepository extends CrudRepository<LedgerAccount, UUID> {

  Optional<LedgerAccount> findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
      @Nullable LedgerParty owner,
      String name,
      AccountPurpose purpose,
      AssetType assetType,
      AccountType accountType);

  List<LedgerAccount> findAllByOwner(LedgerParty owner);

  @Lock(PESSIMISTIC_WRITE)
  @Query("SELECT a FROM LedgerAccount a WHERE a.owner IN :owners ORDER BY a.id")
  List<LedgerAccount> lockAccountsOf(Collection<LedgerParty> owners);

  @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.account = :account")
  BigDecimal sumOfEntries(LedgerAccount account);

  @Query(
      """
      SELECT new ee.tuleva.onboarding.ledger.UnitHoldingChange(
        t.id,
        t.transactionType,
        SUM(CASE WHEN e.account IN :unitAccounts THEN -e.amount ELSE 0 END),
        SUM(CASE WHEN e.account = :subscriptionsAccount THEN -e.amount ELSE 0 END))
      FROM LedgerEntry e JOIN e.transaction t
      WHERE e.account IN :unitAccounts OR e.account = :subscriptionsAccount
      GROUP BY t.id, t.transactionType, t.transactionDate, t.createdAt
      ORDER BY t.transactionDate, t.createdAt, t.id
      """)
  List<UnitHoldingChange> unitHoldingChanges(
      Collection<LedgerAccount> unitAccounts, LedgerAccount subscriptionsAccount);

  @Query(
      """
      SELECT COUNT(a) FROM LedgerAccount a
      WHERE a.name = :name AND a.purpose = :purpose
        AND a IN (SELECT e.account FROM LedgerEntry e GROUP BY e.account HAVING SUM(e.amount) < 0)
      """)
  int countWithPositiveBalance(String name, AccountPurpose purpose);
}
