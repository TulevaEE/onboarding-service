package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional
class LedgerValidationIntegrationTest {

  @Autowired private LedgerTransactionRepository transactionRepository;
  @Autowired private LedgerAccountRepository accountRepository;
  @Autowired private LedgerPartyRepository partyRepository;

  private LedgerParty systemParty;
  private LedgerAccount eurAssetAccount;
  private LedgerAccount eurLiabilityAccount;
  private LedgerAccount fundAssetAccount;
  private LedgerAccount fundLiabilityAccount;

  @BeforeEach
  void setUp() {
    // Create a party for the accounts
    systemParty =
        LedgerParty.builder()
            .partyType(LedgerParty.PartyType.LEGAL_ENTITY)
            .ownerId("TEST-SYSTEM-001")
            .details(Map.of("name", "Test System Party"))
            .build();
    systemParty = partyRepository.save(systemParty);

    // Create test accounts
    eurAssetAccount = createAndPersistAccount("EUR Asset", EUR, ASSET);
    eurLiabilityAccount = createAndPersistAccount("EUR Liability", EUR, LIABILITY);
    fundAssetAccount = createAndPersistAccount("Fund Asset", FUND_UNIT, ASSET);
    fundLiabilityAccount = createAndPersistAccount("Fund Liability", FUND_UNIT, LIABILITY);
  }

  @Test
  @DisplayName("Should fail to persist unbalanced transaction")
  void shouldFailToPersistUnbalancedTransaction() {
    // Given - an unbalanced transaction (100 - 50 ≠ 0)
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-50.00")); // Unbalanced!

    // When/Then - should fail when trying to persist through repository
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("balance");
  }

  @Test
  @DisplayName("Should fail to persist transaction with only one entry")
  void shouldFailToPersistTransactionWithSingleEntry() {
    // Given - a transaction with only one entry
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    // Missing the balancing entry!

    // When/Then - should fail when trying to persist
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("at least 2 entries");
  }

  @Test
  @DisplayName("Should fail to persist transaction with mismatched asset types")
  void shouldFailToPersistTransactionWithMismatchedAssetTypes() {
    // Given - a transaction where EUR doesn't balance (but FUND_UNIT does)
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // EUR: 100 - 50 = 50 (doesn't balance!)
    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-50.00"));

    // FUND_UNIT: 10 - 60 = -50 (compensates EUR but wrong!)
    transaction.addEntry(fundAssetAccount, new BigDecimal("10.0000"));
    transaction.addEntry(fundLiabilityAccount, new BigDecimal("-60.0000"));

    // When/Then - should fail when trying to persist
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("does not balance");
  }

  @Test
  @DisplayName("Should fail to persist transaction with EUR amount having too many decimals")
  void shouldFailToPersistEurWithWrongPrecision() {
    // Given - Transaction with EUR amounts with too many decimal places
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // Manually create entries with wrong precision, bypassing addEntry validation
    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(eurAssetAccount)
            .amount(new BigDecimal("100.999")) // 3 decimal places for EUR!
            .assetType(EUR)
            .transaction(transaction)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .account(eurLiabilityAccount)
            .amount(new BigDecimal("-100.999"))
            .assetType(EUR)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry1);
    transaction.getEntries().add(entry2);

    // When/Then - should fail validation
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  @DisplayName("Should fail to persist transaction with FUND_UNIT amount having too many decimals")
  void shouldFailToPersistFundUnitWithWrongPrecision() {
    // Given - Transaction with FUND_UNIT amounts with too many decimal places
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // Manually create entries with wrong precision
    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(fundAssetAccount)
            .amount(new BigDecimal("10.123456")) // 6 decimal places for FUND_UNIT!
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .account(fundLiabilityAccount)
            .amount(new BigDecimal("-10.123456"))
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry1);
    transaction.getEntries().add(entry2);

    // When/Then - should fail validation
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  @DisplayName("Should fail when trying to add entry with wrong asset type to account")
  void shouldFailToAddWrongAssetTypeToAccount() {
    // Given
    LedgerAccount eurAccount = eurAssetAccount;

    // When/Then - trying to create an entry with wrong asset type
    LedgerEntry wrongEntry =
        LedgerEntry.builder()
            .amount(new BigDecimal("100.00"))
            .assetType(FUND_UNIT) // Wrong! Account is EUR
            .build();

    // Should fail runtime validation when adding to account
    assertThatThrownBy(() -> eurAccount.addEntry(wrongEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Entry asset type FUND_UNIT does not match account asset type EUR");
  }

  @Test
  @DisplayName("Should fail to persist transaction with entry-account asset type mismatch")
  void shouldFailToPersistEntryAccountMismatch() {
    // Given - manually create entries with wrong asset types
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // Bypass addEntry validation by directly creating entries
    LedgerEntry mismatchedEntry =
        LedgerEntry.builder()
            .account(eurAssetAccount) // EUR account
            .amount(new BigDecimal("100.00"))
            .assetType(FUND_UNIT) // But FUND_UNIT asset type!
            .transaction(transaction)
            .build();

    LedgerEntry balancingEntry =
        LedgerEntry.builder()
            .account(fundLiabilityAccount)
            .amount(new BigDecimal("-100.00"))
            .assetType(EUR) // Wrong again!
            .transaction(transaction)
            .build();

    transaction.getEntries().add(mismatchedEntry);
    transaction.getEntries().add(balancingEntry);

    // When/Then - validation should fail
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  @DisplayName("Should successfully persist valid balanced transaction")
  void shouldPersistValidBalancedTransaction() {
    // Given - a properly balanced transaction
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));

    // When
    LedgerTransaction persisted = transactionRepository.save(transaction);

    // Then
    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.getEntries()).hasSize(2);
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Should successfully persist multi-currency balanced transaction")
  void shouldPersistMultiCurrencyBalancedTransaction() {
    // Given - a transaction with multiple asset types, each balanced
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // EUR entries balance: 100 - 100 = 0
    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));

    // FUND_UNIT entries balance: 10 - 10 = 0
    transaction.addEntry(fundAssetAccount, new BigDecimal("10.12345")); // Valid 5 decimals
    transaction.addEntry(fundLiabilityAccount, new BigDecimal("-10.12345"));

    // When
    LedgerTransaction persisted = transactionRepository.save(transaction);

    // Then
    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.getEntries()).hasSize(4);
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Should enforce zero balance even with many entries")
  void shouldEnforceZeroBalanceWithManyEntries() {
    // Given - a complex transaction that must still balance
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    // Multiple EUR debits and credits
    transaction.addEntry(eurAssetAccount, new BigDecimal("50.00"));
    transaction.addEntry(eurAssetAccount, new BigDecimal("25.00"));
    transaction.addEntry(eurAssetAccount, new BigDecimal("25.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));

    // When
    LedgerTransaction persisted = transactionRepository.save(transaction);

    // Then
    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Should fail with small rounding imbalance")
  void shouldFailWithSmallRoundingImbalance() {
    // Given - a transaction with tiny imbalance
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-99.99")); // Off by 0.01!

    // When/Then
    assertThatThrownBy(() -> transactionRepository.save(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("balance");
  }

  // Helper method to create and persist an account
  private LedgerAccount createAndPersistAccount(
      String name, LedgerAccount.AssetType assetType, LedgerAccount.AccountType accountType) {

    LedgerAccount account =
        LedgerAccount.builder()
            .name(name)
            .purpose(SYSTEM_ACCOUNT)
            .accountType(accountType)
            .owner(null) // SYSTEM_ACCOUNT must have null owner per database constraint
            .assetType(assetType)
            .build();

    return accountRepository.save(account);
  }
}
