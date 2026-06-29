package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;
  private final JdbcClient jdbcClient;

  LedgerAccount createUserAccount(LedgerParty owner, UserAccount userAccount) {
    insertUserAccountIfAbsent(owner, userAccount);
    return findUserAccount(owner, userAccount).orElseThrow();
  }

  void insertUserAccountIfAbsent(LedgerParty owner, UserAccount userAccount) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.account (id, owner_party_id, name, purpose, account_type, asset_type)
            VALUES (:id, :ownerPartyId, :name,
                    CAST(:purpose AS ledger.account_purpose),
                    CAST(:accountType AS ledger.account_type),
                    CAST(:assetType AS ledger.asset_type))
            ON CONFLICT DO NOTHING
            """)
        .param("id", UUID.randomUUID())
        .param("ownerPartyId", owner.getId())
        .param("name", userAccount.name())
        .param("purpose", USER_ACCOUNT.name())
        .param("accountType", userAccount.getAccountType().name())
        .param("assetType", userAccount.getAssetType().name())
        .update();
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

  Optional<LedgerAccount> findSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return findSystemAccountByName(
        systemAccount.getAccountName(fund),
        systemAccount.getAccountType(),
        systemAccount.getAssetType());
  }

  LedgerAccount createSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return createSystemAccount(
        systemAccount.getAccountName(fund),
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
