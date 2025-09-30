package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.*;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  LedgerAccount createAccount(
      LedgerParty owner, String name, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .owner(owner)
            .purpose(USER_ACCOUNT)
            .assetType(assetType)
            .accountType(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public Optional<LedgerAccount> getLedgerAccount(
      LedgerParty owner, AccountType accountType, AssetType assetTypeCode) {
    return ledgerAccountRepository.findByOwnerAndAccountTypeAndAssetTypeWithEntries(
        owner, accountType, assetTypeCode);
  }

  List<LedgerAccount> getAccounts(LedgerParty owner) {
    return ledgerAccountRepository.findAllByOwner(owner);
  }

  public Optional<LedgerAccount> findSystemAccount(
      String name, AccountPurpose accountPurpose, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository.findByNameAndPurposeAndAssetTypeAndAccountTypeWithEntries(
        name, accountPurpose, assetType, accountType);
  }

  public LedgerAccount createSystemAccount(
      String name, AccountPurpose accountPurpose, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .purpose(accountPurpose)
            .assetType(assetType)
            .accountType(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }
}
