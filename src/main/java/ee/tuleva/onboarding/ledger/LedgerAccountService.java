package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  LedgerAccount createUserAccount(LedgerParty owner, UserAccount userAccount) {
    var ledgerAccount =
        LedgerAccount.builder()
            .owner(owner)
            .name(userAccount.name())
            .purpose(USER_ACCOUNT)
            .assetType(userAccount.getAssetType())
            .accountType(userAccount.getAccountType())
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public Optional<LedgerAccount> findUserAccount(LedgerParty owner, UserAccount userAccount) {
    return ledgerAccountRepository.findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
        owner,
        userAccount.name(),
        USER_ACCOUNT,
        userAccount.getAssetType(),
        userAccount.getAccountType());
  }

  List<LedgerAccount> getAccounts(LedgerParty owner) {
    return ledgerAccountRepository.findAllByOwner(owner);
  }

  public Optional<LedgerAccount> findSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountRepository.findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
        null,
        systemAccount.getAccountName(),
        SYSTEM_ACCOUNT,
        systemAccount.getAssetType(),
        systemAccount.getAccountType());
  }

  public LedgerAccount createSystemAccount(SystemAccount systemAccount) {
    return createSystemAccount(
        systemAccount.getAccountName(),
        systemAccount.getAccountType(),
        systemAccount.getAssetType());
  }

  Optional<LedgerAccount> findSystemAccountByName(
      String name, AccountType accountType, AssetType assetType) {
    return ledgerAccountRepository.findByOwnerAndNameAndPurposeAndAssetTypeAndAccountType(
        null, name, SYSTEM_ACCOUNT, assetType, accountType);
  }

  int countAccountsWithPositiveBalance(UserAccount userAccount) {
    return ledgerAccountRepository.countWithPositiveBalance(userAccount.name(), USER_ACCOUNT);
  }

  LedgerAccount createSystemAccount(String name, AccountType accountType, AssetType assetType) {
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
