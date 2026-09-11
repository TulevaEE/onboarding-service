package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

  @Query(
      """
      SELECT COUNT(a) FROM LedgerAccount a
      WHERE a.name = :name AND a.purpose = :purpose
        AND a IN (SELECT e.account FROM LedgerEntry e GROUP BY e.account HAVING SUM(e.amount) < 0)
      """)
  int countWithPositiveBalance(String name, AccountPurpose purpose);

  /**
   * The account's committed balance, summed in the database.
   *
   * <p>Not {@code LedgerAccount.getBalance()}: that walks the account's whole in-memory entry
   * collection, so calling it on every write would be O(history) per transaction and would get
   * slower forever.
   */
  @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.account = :account")
  BigDecimal balanceOf(LedgerAccount account);

  /**
   * Takes the account's row lock, so that a balance read and the write that depends on it cannot be
   * interleaved by another transaction.
   *
   * <p>Deliberately projects the id rather than the entity: locking through {@code findById} loads
   * and refreshes the managed {@code LedgerAccount}, which discards entries the current unit of
   * work has not flushed yet and corrupts the posting in progress.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a.id FROM LedgerAccount a WHERE a.id = :id")
  Optional<UUID> lockAccount(UUID id);
}
