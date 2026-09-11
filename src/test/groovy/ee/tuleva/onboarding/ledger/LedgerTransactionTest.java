package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
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

  @Test
  void addEntry_normalizesAtExactBoundaryPrecision() {
    var account = fundUnitAccount();
    var transaction = sampleTransaction();

    var entry = transaction.addEntry(account, new BigDecimal("1000.1234500000000"));

    assertThat(entry.getAmount().scale()).isEqualTo(5);
    assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("1000.12345"));
  }

  @Test
  void findNavPerUnit_returnsParsedNavFromMetadata() {
    var transaction =
        LedgerTransaction.builder()
            .transactionDate(Instant.now())
            .metadata(Map.of("navPerUnit", "123.45"))
            .build();

    assertThat(transaction.findNavPerUnit()).contains(new BigDecimal("123.45"));
  }

  @Test
  void findNavPerUnit_isEmptyWhenMetadataMissingNavPerUnit() {
    var transaction = sampleTransaction();

    assertThat(transaction.findNavPerUnit()).isEmpty();
  }

  @Test
  void findUserFundUnits_returnsAbsoluteAmountOfTheUserFundUnitEntry() {
    var transaction = sampleTransaction();
    var systemFundUnitAccount =
        LedgerAccount.builder()
            .name("SYSTEM_FUND_UNITS")
            .purpose(SYSTEM_ACCOUNT)
            .assetType(FUND_UNIT)
            .accountType(ASSET)
            .build();
    var userEurAccount =
        LedgerAccount.builder()
            .name("USER_EUR")
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(ASSET)
            .build();
    var userFundUnitAccount =
        LedgerAccount.builder()
            .name("USER_FUND_UNITS")
            .purpose(USER_ACCOUNT)
            .assetType(FUND_UNIT)
            .accountType(LIABILITY)
            .build();
    transaction.addEntry(systemFundUnitAccount, new BigDecimal("500.00000"));
    transaction.addEntry(userEurAccount, new BigDecimal("10.00"));
    transaction.addEntry(userFundUnitAccount, new BigDecimal("-7.50000"));

    assertThat(transaction.findUserFundUnits()).contains(new BigDecimal("7.50000"));
  }

  @Test
  void findUserFundUnits_isEmptyWhenNoUserFundUnitEntryExists() {
    var transaction = sampleTransaction();
    var userEurAccount =
        LedgerAccount.builder()
            .name("USER_EUR")
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(ASSET)
            .build();
    transaction.addEntry(userEurAccount, new BigDecimal("10.00"));

    assertThat(transaction.findUserFundUnits()).isEmpty();
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
