package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LedgerAccountTest {

  @Test
  void isUserAccount_isTrueForUserAccounts() {
    var account =
        LedgerAccount.builder()
            .name("USER_CASH")
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(ASSET)
            .build();

    assertThat(account.isUserAccount()).isTrue();
  }

  @Test
  void isUserAccount_isFalseForSystemAccounts() {
    var account =
        LedgerAccount.builder()
            .name("SYSTEM_CASH")
            .purpose(SYSTEM_ACCOUNT)
            .assetType(EUR)
            .accountType(ASSET)
            .build();

    assertThat(account.isUserAccount()).isFalse();
  }

  @Test
  void addEntry_populatesEntryAccountAndAssetType() {
    var account =
        LedgerAccount.builder()
            .name("USER_CASH")
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(ASSET)
            .build();
    var entry = LedgerEntry.builder().amount(new BigDecimal("100.00")).build();

    account.addEntry(entry);

    assertThat(entry.getAccount()).isEqualTo(account);
    assertThat(entry.getAssetType()).isEqualTo(EUR);
    assertThat(account.getEntries()).containsExactly(entry);
  }
}
