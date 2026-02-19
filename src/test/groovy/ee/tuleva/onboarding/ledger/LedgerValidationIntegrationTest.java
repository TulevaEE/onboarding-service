package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
    systemParty =
        LedgerParty.builder()
            .partyType(LedgerParty.PartyType.LEGAL_ENTITY)
            .ownerId("TEST-SYSTEM-001")
            .details(Map.of("name", "Test System Party"))
            .build();
    systemParty = partyRepository.save(systemParty);

    eurAssetAccount = createAndPersistAccount("EUR Asset", EUR, ASSET);
    eurLiabilityAccount = createAndPersistAccount("EUR Liability", EUR, LIABILITY);
    fundAssetAccount = createAndPersistAccount("Fund Asset", FUND_UNIT, ASSET);
    fundLiabilityAccount = createAndPersistAccount("Fund Liability", FUND_UNIT, LIABILITY);
  }

  @Test
  void shouldFailToPersistUnbalancedTransaction() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-50.00"));

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("balance");
  }

  @Test
  void shouldFailToPersistTransactionWithSingleEntry() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("at least 2 entries");
  }

  @Test
  void shouldFailToPersistTransactionWithMismatchedAssetTypes() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-50.00"));
    transaction.addEntry(fundAssetAccount, new BigDecimal("10.00000"));
    transaction.addEntry(fundLiabilityAccount, new BigDecimal("-60.00000"));

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("does not balance");
  }

  @Test
  void shouldFailToPersistEurWithWrongPrecision() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(eurAssetAccount)
            .amount(new BigDecimal("100.999"))
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

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  void shouldFailToPersistFundUnitWithWrongPrecision() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(fundAssetAccount)
            .amount(new BigDecimal("10.123456"))
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

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  void shouldFailToAddWrongAssetTypeToAccount() {
    LedgerEntry wrongEntry =
        LedgerEntry.builder().amount(new BigDecimal("100.00")).assetType(FUND_UNIT).build();

    assertThatThrownBy(() -> eurAssetAccount.addEntry(wrongEntry))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailToPersistEntryAccountMismatch() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    LedgerEntry mismatchedEntry =
        LedgerEntry.builder()
            .account(eurAssetAccount)
            .amount(new BigDecimal("100.00"))
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    LedgerEntry balancingEntry =
        LedgerEntry.builder()
            .account(fundLiabilityAccount)
            .amount(new BigDecimal("-100.00"))
            .assetType(EUR)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(mismatchedEntry);
    transaction.getEntries().add(balancingEntry);

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void shouldPersistValidBalancedTransaction() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));

    LedgerTransaction persisted = transactionRepository.save(transaction);

    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.getEntries()).hasSize(2);
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  void shouldPersistMultiCurrencyBalancedTransaction() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));
    transaction.addEntry(fundAssetAccount, new BigDecimal("10.12345"));
    transaction.addEntry(fundLiabilityAccount, new BigDecimal("-10.12345"));

    LedgerTransaction persisted = transactionRepository.save(transaction);

    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.getEntries()).hasSize(4);
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  void shouldFailToPersistFundUnitWithTooFewDecimals() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(fundAssetAccount)
            .amount(new BigDecimal("10.123"))
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .account(fundLiabilityAccount)
            .amount(new BigDecimal("-10.123"))
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry1);
    transaction.getEntries().add(entry2);

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  void shouldEnforceZeroBalanceWithManyEntries() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("50.00"));
    transaction.addEntry(eurAssetAccount, new BigDecimal("25.00"));
    transaction.addEntry(eurAssetAccount, new BigDecimal("25.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-100.00"));

    LedgerTransaction persisted = transactionRepository.save(transaction);

    assertThat(persisted.getId()).isNotNull();
    assertThat(persisted.sum()).isEqualByComparingTo(ZERO);
  }

  @Test
  void shouldFailWithSmallRoundingImbalance() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();

    transaction.addEntry(eurAssetAccount, new BigDecimal("100.00"));
    transaction.addEntry(eurLiabilityAccount, new BigDecimal("-99.99"));

    assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("balance");
  }

  private LedgerAccount createAndPersistAccount(
      String name, AssetType assetType, AccountType accountType) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(name)
            .purpose(SYSTEM_ACCOUNT)
            .accountType(accountType)
            .owner(null)
            .assetType(assetType)
            .build();

    return accountRepository.save(account);
  }
}
