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

  LedgerAccount findByLedgerPartyAndTypeAndAssetTypeCode(
      LedgerParty ledgerParty, AccountType accountType, AssetType assetTypeCode);

  Optional<LedgerAccount> findByNameAndAccountPurposeAndAssetTypeCodeAndType(
      String name, AccountPurpose accountPurpose, AssetType assetTypeCode, AccountType accountType);

  List<LedgerAccount> findAllByLedgerParty(LedgerParty ledgerParty);
}
