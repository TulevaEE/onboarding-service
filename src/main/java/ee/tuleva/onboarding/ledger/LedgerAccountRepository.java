package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Profile({"dev", "test"})
@Repository
interface LedgerAccountRepository extends CrudRepository<LedgerAccount, UUID> {

  Optional<LedgerAccount> findByOwnerAndAccountTypeAndAssetType(
      LedgerParty owner, AccountType accountType, AssetType assetType);

  Optional<LedgerAccount> findByNameAndPurposeAndAssetTypeAndAccountType(
      String name, AccountPurpose purpose, AssetType assetType, AccountType accountType);

  List<LedgerAccount> findAllByOwner(LedgerParty owner);
}
