package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.validation.AssetTypeConsistencyValidator;
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
class AssetTypeConsistencyValidatorTest {

  private Validator validator;
  private AssetTypeConsistencyValidator customValidator;

  @Mock private ConstraintValidatorContext context;

  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    customValidator = new AssetTypeConsistencyValidator();
    customValidator.initialize(null);
  }

  @Test
  @DisplayName("Should validate when all entries match account asset types")
  void shouldValidateWhenAssetTypesMatch() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);

    // Add entries - asset types will match because addEntry uses account's asset type
    transaction.addEntry(eurAccount1, new BigDecimal("-100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("100.00"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then - Should have no asset type consistency violations
    // (might have other violations like balance, but not asset type)
    boolean hasAssetTypeViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("asset type"));
    assertThat(hasAssetTypeViolation).isFalse();
  }

  @Test
  @DisplayName("Should fail validation when entry asset type doesn't match account")
  void shouldFailWhenAssetTypeMismatch() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount = createAccount(EUR, ASSET);
    LedgerAccount fundAccount = createAccount(FUND_UNIT, ASSET);

    // Create entries with correct asset types first
    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(eurAccount)
            .amount(new BigDecimal("-100.00"))
            .assetType(FUND_UNIT) // Deliberately wrong asset type
            .transaction(transaction)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .account(fundAccount)
            .amount(new BigDecimal("100.00"))
            .assetType(EUR) // Deliberately wrong asset type
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry1);
    transaction.getEntries().add(entry2);

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context, times(2)).buildConstraintViolationWithTemplate(anyString());
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
  @DisplayName("Should handle transaction with null entries")
  void shouldHandleNullEntries() {
    // Given
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(TRANSFER)
            .transactionDate(Instant.now())
            .build();

    // When
    boolean isValid = customValidator.isValid(transaction, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should validate mixed asset types when each matches its account")
  void shouldValidateMixedAssetTypes() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);
    LedgerAccount fundAccount1 = createAccount(FUND_UNIT, ASSET);
    LedgerAccount fundAccount2 = createAccount(FUND_UNIT, LIABILITY);

    // Add entries with matching asset types
    transaction.addEntry(eurAccount1, new BigDecimal("100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("-100.00"));
    transaction.addEntry(fundAccount1, new BigDecimal("10.0000"));
    transaction.addEntry(fundAccount2, new BigDecimal("-10.0000"));

    // When
    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    // Then - Should have no asset type consistency violations
    boolean hasAssetTypeViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("asset type"));
    assertThat(hasAssetTypeViolation).isFalse();
  }

  @Test
  @DisplayName("Should throw exception when adding entry with null account")
  void shouldThrowExceptionForNullAccount() {
    // Given
    LedgerTransaction transaction = createTransaction();

    // When/Then
    assertThatThrownBy(() -> transaction.addEntry(null, new BigDecimal("100.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Account cannot be null");
  }

  @Test
  @DisplayName("Should throw exception when adding entry with null amount")
  void shouldThrowExceptionForNullAmount() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account = createAccount(EUR, ASSET);

    // When/Then
    assertThatThrownBy(() -> transaction.addEntry(account, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Amount cannot be null");
  }

  @Test
  @DisplayName("Should automatically set entry asset type from account")
  void shouldAutoSetAssetTypeFromAccount() {
    // Given
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount = createAccount(EUR, ASSET);
    LedgerAccount fundAccount = createAccount(FUND_UNIT, LIABILITY);

    // When
    LedgerEntry eurEntry = transaction.addEntry(eurAccount, new BigDecimal("100.00"));
    LedgerEntry fundEntry = transaction.addEntry(fundAccount, new BigDecimal("-10.0000"));

    // Then
    assertThat(eurEntry.getAssetType()).isEqualTo(EUR);
    assertThat(fundEntry.getAssetType()).isEqualTo(FUND_UNIT);
  }

  // Helper methods

  private LedgerTransaction createTransaction() {
    return LedgerTransaction.builder()
        .transactionType(TRANSFER)
        .transactionDate(Instant.now())
        .build();
  }

  private LedgerAccount createAccount(
      LedgerAccount.AssetType assetType, LedgerAccount.AccountType accountType) {
    LedgerParty party = LedgerParty.builder().build();

    return LedgerAccount.builder()
        .owner(party)
        .assetType(assetType)
        .accountType(accountType)
        .build();
  }
}
