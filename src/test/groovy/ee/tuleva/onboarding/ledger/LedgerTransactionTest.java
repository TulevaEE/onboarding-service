package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerTransactionTest {

  @Test
  void addEntry_normalizesFundUnitScaleFromTrailingZeros() {
    var account = fundUnitAccount();
    var transaction = sampleTransaction();

    var entry = transaction.addEntry(account, new BigDecimal("1034931.00000000"));

    assertThat(entry.getAmount().scale()).isEqualTo(5);
    assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("1034931.00000"));
  }

  @Test
  void addEntry_preservesFundUnitScaleWhenAlreadyCorrect() {
    var account = fundUnitAccount();
    var transaction = sampleTransaction();

    var entry = transaction.addEntry(account, new BigDecimal("1000.12345"));

    assertThat(entry.getAmount().scale()).isEqualTo(5);
    assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("1000.12345"));
  }

  @Test
  void addEntry_doesNotNormalizeFundUnitWithSignificantExcessPrecision() {
    var account = fundUnitAccount();
    var transaction = sampleTransaction();

    var entry = transaction.addEntry(account, new BigDecimal("1000.123456"));

    assertThat(entry.getAmount().scale()).isEqualTo(6);
  }

  @Test
  void addEntry_normalizesEurScaleFromTrailingZeros() {
    var account = eurAccount();
    var transaction = sampleTransaction();

    var entry = transaction.addEntry(account, new BigDecimal("100.0000"));

    assertThat(entry.getAmount().scale()).isEqualTo(2);
    assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  private static LedgerAccount fundUnitAccount() {
    return LedgerAccount.builder()
        .name("TEST_FUND_UNITS")
        .purpose(SYSTEM_ACCOUNT)
        .assetType(FUND_UNIT)
        .accountType(ASSET)
        .build();
  }

  private static LedgerAccount eurAccount() {
    return LedgerAccount.builder()
        .name("TEST_EUR")
        .purpose(SYSTEM_ACCOUNT)
        .assetType(EUR)
        .accountType(ASSET)
        .build();
  }

  private static LedgerTransaction sampleTransaction() {
    return LedgerTransaction.builder().transactionDate(Instant.now()).metadata(Map.of()).build();
  }
}
