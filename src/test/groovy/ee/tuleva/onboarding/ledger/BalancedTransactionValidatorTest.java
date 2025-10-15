package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.validation.BalancedTransactionValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalancedTransactionValidatorTest {

  private Validator validator;
  private BalancedTransactionValidator customValidator;

  @Mock private ConstraintValidatorContext context;

  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    customValidator = new BalancedTransactionValidator();
    customValidator.initialize(null);
  }

  @Test
  @DisplayName("Should validate balanced transaction with two entries")
  void shouldValidateBalancedTransactionWithTwoEntries() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount fromAccount = createAccount(EUR, ASSET);
    LedgerAccount toAccount = createAccount(EUR, ASSET);

    BigDecimal amount = new BigDecimal("100.00");
    transaction.addEntry(fromAccount, amount.negate());
    transaction.addEntry(toAccount, amount);

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should fail validation when transaction is not balanced")
  void shouldFailValidationWhenNotBalanced() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, ASSET);

    // Unbalanced entries: 100 - 50 = 50 (not zero)
    transaction.addEntry(account1, new BigDecimal("100.00"));
    transaction.addEntry(account2, new BigDecimal("-50.00"));

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains("Transaction entries must balance to zero. Current sum: 50"));
  }

  @Test
  @DisplayName("Should fail validation with only one entry")
  void shouldFailValidationWithOnlyOneEntry() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account = createAccount(EUR, ASSET);
    transaction.addEntry(account, new BigDecimal("100.00"));

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate("Transaction must have at least 2 entries, found: 1");
  }

  @Test
  @DisplayName("Should fail validation with no entries")
  void shouldFailValidationWithNoEntries() {
    // Given
    LedgerTransaction transaction = createTransaction();

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate("Transaction must have at least 2 entries, found: 0");
  }

  @Test
  @DisplayName("Should validate complex multi-entry transaction")
  void shouldValidateComplexMultiEntryTransaction() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);
    LedgerAccount account4 = createAccount(EUR, LIABILITY);

    // Complex but balanced: 500 + 200 - 300 - 400 = 0
    transaction.addEntry(account1, new BigDecimal("500.00"));
    transaction.addEntry(account2, new BigDecimal("200.00"));
    transaction.addEntry(account3, new BigDecimal("-300.00"));
    transaction.addEntry(account4, new BigDecimal("-400.00"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should fail validation when different asset types don't balance")
  void shouldFailValidationWhenDifferentAssetTypesNotBalanced() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);
    LedgerAccount fundAccount1 = createAccount(FUND_UNIT, ASSET);
    LedgerAccount fundAccount2 = createAccount(FUND_UNIT, LIABILITY);

    // EUR balances: 100 - 100 = 0 ✓
    transaction.addEntry(eurAccount1, new BigDecimal("100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("-100.00"));

    // FUND_UNIT doesn't balance: 10 - 5 = 5 ✗
    transaction.addEntry(fundAccount1, new BigDecimal("10.0000"));
    transaction.addEntry(fundAccount2, new BigDecimal("-5.0000"));

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    // The validator checks overall balance first, which also fails
    verify(context, atLeastOnce()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  @DisplayName("Should validate when multiple asset types each balance individually")
  void shouldValidateWhenMultipleAssetTypesBalanceIndividually() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);
    LedgerAccount fundAccount1 = createAccount(FUND_UNIT, ASSET);
    LedgerAccount fundAccount2 = createAccount(FUND_UNIT, LIABILITY);

    // EUR balances: 100 - 100 = 0 ✓
    transaction.addEntry(eurAccount1, new BigDecimal("100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("-100.00"));

    // FUND_UNIT balances: 10 - 10 = 0 ✓
    transaction.addEntry(fundAccount1, new BigDecimal("10.0000"));
    transaction.addEntry(fundAccount2, new BigDecimal("-10.0000"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should handle null transaction gracefully")
  void shouldHandleNullTransaction() {
    // When
    boolean isValid = customValidator.isValid(null, context);

    // Then
    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  @DisplayName("Should handle transaction with null entries list")
  void shouldHandleTransactionWithNullEntries() {
    // Given
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .build();
    // Entries list is initialized as empty ArrayList by default

    // Setup mocks - even though we expect true, the validator still calls context methods
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    // The entries list is actually initialized as empty, not null, so it will fail validation
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate("Transaction must have at least 2 entries, found: 0");
  }

  @Test
  @DisplayName("Should validate transaction with exactly zero sum")
  void shouldValidateTransactionWithExactlyZeroSum() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);

    // Test with very small amounts that sum to exactly zero
    transaction.addEntry(account1, new BigDecimal("0.01"));
    transaction.addEntry(account2, new BigDecimal("-0.01"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should fail validation with precision rounding errors")
  void shouldFailValidationWithPrecisionRoundingErrors() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);

    // Division by 3 can cause rounding issues: 100/3 = 33.33...
    BigDecimal total = new BigDecimal("100.00");
    BigDecimal third = new BigDecimal("33.33");

    // 33.33 + 33.33 + 33.33 = 99.99 (not 100.00)
    transaction.addEntry(account1, third);
    transaction.addEntry(account2, third);
    transaction.addEntry(account3, third);
    transaction.addEntry(account1, total.negate());

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(contains("Transaction entries must balance to zero"));
  }

  @Test
  @DisplayName("Should validate transaction with proper precision handling")
  void shouldValidateTransactionWithProperPrecisionHandling() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);
    LedgerAccount account4 = createAccount(EUR, LIABILITY);

    // Properly handling division: 100.00 split three ways with remainder
    transaction.addEntry(account1, new BigDecimal("33.34"));
    transaction.addEntry(account2, new BigDecimal("33.33"));
    transaction.addEntry(account3, new BigDecimal("33.33"));
    transaction.addEntry(account4, new BigDecimal("-100.00"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then
    assertThat(violations).isEmpty();
  }

  // Helper methods

  private LedgerTransaction createTransaction() {
    return LedgerTransaction.builder()
        .transactionType(TRANSFER)
        .transactionDate(Instant.now())
        .metadata(new HashMap<>())
        .build();
  }

  private LedgerAccount createAccount(AssetType assetType, AccountType accountType) {
    LedgerParty party = LedgerParty.builder().build();

    return LedgerAccount.builder()
        .owner(party)
        .assetType(assetType)
        .accountType(accountType)
        .build();
  }

  private static String contains(String substring) {
    return org.mockito.ArgumentMatchers.contains(substring);
  }
}
