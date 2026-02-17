package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LedgerAccountRepository extends CrudRepository<LedgerAccount, UUID> {

  Optional<LedgerAccount> findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
      LedgerParty owner,
      String name,
      AccountPurpose purpose,
      AssetType assetType,
      AccountType accountType);

  List<LedgerAccount> findAllByOwner(LedgerParty owner);

  @Query(
      """
      SELECT COUNT(a) FROM LedgerAccount a
      WHERE a.name = :name AND a.purpose = :purpose
        AND (SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.account = a) < 0
      """)
  int countWithPositiveBalance(String name, AccountPurpose purpose);
}
