package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GeneralLedgerAccounts {

  private final LedgerAccountRepository accountRepository;
  private final LedgerAccountService accountService;

  void upsert(String entity, List<GeneralLedgerAccount> accounts) {
    accounts.forEach(account -> upsert(entity, account));
  }

  Set<String> codesOf(String entity) {
    int prefixLength = accountName(entity, "").length();
    return accountRepository
        .findNamesByPurposeWithoutOwner(SYSTEM_ACCOUNT, accountNamePattern(entity))
        .stream()
        .map(name -> name.substring(prefixLength))
        .collect(toSet());
  }

  static String accountNamePattern(String entity) {
    return accountName(entity, "").replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%")
        + "%";
  }

  LedgerAccount resolve(String entity, String code) {
    String name = accountName(entity, code);
    return accountRepository
        .findByNameAndPurposeAndOwnerIsNull(name, SYSTEM_ACCOUNT)
        .orElseThrow(
            () -> new IllegalStateException("General ledger account missing: name=" + name));
  }

  private void upsert(String entity, GeneralLedgerAccount account) {
    String name = accountName(entity, account.code());
    var ledgerAccount =
        accountRepository
            .findByNameAndPurposeAndOwnerIsNull(name, SYSTEM_ACCOUNT)
            .orElseGet(() -> accountService.createSystemAccount(name, account.accountType(), EUR));
    if (ledgerAccount.getAccountType() != account.accountType()) {
      throw new IllegalStateException(
          "General ledger account reclassified: name="
              + name
              + ", accountType="
              + ledgerAccount.getAccountType()
              + ", sourceAccountType="
              + account.accountType());
    }
    ledgerAccount.updateMetadata(account.metadata());
  }

  private static String accountName(String entity, String code) {
    return "GENERAL_LEDGER:" + entity + ":" + code;
  }
}
