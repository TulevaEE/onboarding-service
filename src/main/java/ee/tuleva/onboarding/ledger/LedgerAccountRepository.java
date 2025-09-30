package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Profile({"dev", "test"})
@Repository
interface LedgerAccountRepository extends CrudRepository<LedgerAccount, UUID> {

  LedgerAccount findByOwnerAndAccountTypeAndAssetType(
      LedgerParty owner, AccountType accountType, AssetType assetType);

  Optional<LedgerAccount> findByNameAndPurposeAndAssetTypeAndAccountType(
      String name, AccountPurpose purpose, AssetType assetType, AccountType accountType);

  @Query(
      "SELECT a FROM LedgerAccount a LEFT JOIN FETCH a.entries WHERE a.name = :name AND a.purpose = :purpose AND a.assetType = :assetType AND a.accountType = :accountType")
  Optional<LedgerAccount> findByNameAndPurposeAndAssetTypeAndAccountTypeWithEntries(
      String name, AccountPurpose purpose, AssetType assetType, AccountType accountType);

  @Query(
      "SELECT a FROM LedgerAccount a LEFT JOIN FETCH a.entries WHERE a.owner = :owner AND a.accountType = :accountType AND a.assetType = :assetType")
  Optional<LedgerAccount> findByOwnerAndAccountTypeAndAssetTypeWithEntries(
      LedgerParty owner, AccountType accountType, AssetType assetType);

  List<LedgerAccount> findAllByOwner(LedgerParty owner);
}
