package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.*;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerService.SystemAccount;
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

  LedgerAccount createAccount(LedgerParty owner, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .owner(owner)
            .purpose(USER_ACCOUNT)
            .assetType(assetType)
            .accountType(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public Optional<LedgerAccount> getLedgerAccount(
      LedgerParty owner, AccountType accountType, AssetType assetTypeCode) {
    return ledgerAccountRepository.findByOwnerAndAccountTypeAndAssetType(
        owner, accountType, assetTypeCode);
  }

  List<LedgerAccount> getAccounts(LedgerParty owner) {
    return ledgerAccountRepository.findAllByOwner(owner);
  }

  public Optional<LedgerAccount> findSystemAccount(
      SystemAccount systemAccount, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository.findByNameAndPurposeAndAssetTypeAndAccountType(
        systemAccount.name(), SYSTEM_ACCOUNT, assetType, accountType);
  }

  public LedgerAccount createSystemAccount(
      String name, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .purpose(SYSTEM_ACCOUNT)
            .assetType(assetType)
            .accountType(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }
}
