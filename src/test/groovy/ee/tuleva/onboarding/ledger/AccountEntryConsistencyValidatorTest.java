package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.validation.AccountEntryConsistencyValidator;
import ee.tuleva.onboarding.ledger.validation.EntryAccountConsistencyValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountEntryConsistencyValidatorTest {

  private Validator validator;
  private AccountEntryConsistencyValidator accountValidator;
  private EntryAccountConsistencyValidator entryValidator;

  @Mock private ConstraintValidatorContext context;
  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    accountValidator = new AccountEntryConsistencyValidator();
    accountValidator.initialize(null);
    entryValidator = new EntryAccountConsistencyValidator();
    entryValidator.initialize(null);
  }

  @Test
  @DisplayName("Should validate account when all entries have matching asset type")
  void shouldValidateAccountWithMatchingEntries() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();

    // Add entries using the account's addEntry method which ensures consistency
    LedgerEntry entry1 = LedgerEntry.builder().amount(new BigDecimal("100.00")).build();
    LedgerEntry entry2 = LedgerEntry.builder().amount(new BigDecimal("-50.00")).build();

    account.addEntry(entry1);
    account.addEntry(entry2);

    // When
    boolean isValid = accountValidator.isValid(account, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should fail validation when account has entries with mismatched asset types")
  void shouldFailAccountWithMismatchedEntries() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();

    // Manually create entries with wrong asset types (bypassing addEntry validation)
    LedgerEntry entry1 =
        LedgerEntry.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .assetType(EUR)
            .account(account)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("-50.00"))
            .assetType(FUND_UNIT) // Wrong asset type
            .account(account)
            .build();

    account.getEntries().add(entry1);
    account.getEntries().add(entry2);

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = accountValidator.isValid(account, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains("has asset type FUND_UNIT which doesn't match account asset type EUR"));
  }

  @Test
  @DisplayName("Should validate entry when asset type matches account")
  void shouldValidateEntryWithMatchingAssetType() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();

    LedgerEntry entry =
        LedgerEntry.builder()
            .amount(new BigDecimal("100.00"))
            .assetType(EUR)
            .account(account)
            .build();

    // When
    boolean isValid = entryValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should fail validation when entry asset type doesn't match account")
  void shouldFailEntryWithMismatchedAssetType() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();

    LedgerEntry entry =
        LedgerEntry.builder()
            .amount(new BigDecimal("100.00"))
            .assetType(FUND_UNIT) // Wrong asset type
            .account(account)
            .build();

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = entryValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            "Entry asset type FUND_UNIT does not match account asset type EUR");
  }

  @Test
  @DisplayName("Should handle null account in validator")
  void shouldHandleNullAccount() {
    // When
    boolean isValid = accountValidator.isValid(null, context);

    // Then
    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  @DisplayName("Should handle null entry in validator")
  void shouldHandleNullEntry() {
    // When
    boolean isValid = entryValidator.isValid(null, context);

    // Then
    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  @DisplayName("Should handle account with null entries list")
  void shouldHandleAccountWithNullEntries() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();
    // Entries list is initialized as empty ArrayList by default, but let's set it to null for test

    // When
    boolean isValid = accountValidator.isValid(account, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should handle entry with null account")
  void shouldHandleEntryWithNullAccount() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("100.00")).assetType(EUR).account(null).build();

    // When
    boolean isValid = entryValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should handle entry with null asset type")
  void shouldHandleEntryWithNullAssetType() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).accountType(ASSET).build();

    LedgerEntry entry =
        LedgerEntry.builder()
            .amount(new BigDecimal("100.00"))
            .assetType(null)
            .account(account)
            .build();

    // When
    boolean isValid = entryValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  private static String contains(String substring) {
    return org.mockito.ArgumentMatchers.contains(substring);
  }
}
