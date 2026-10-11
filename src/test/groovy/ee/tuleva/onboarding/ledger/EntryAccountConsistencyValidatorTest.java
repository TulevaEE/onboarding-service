package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.validation.EntryAccountConsistencyValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntryAccountConsistencyValidatorTest {

  private EntryAccountConsistencyValidator entryValidator;

  @Mock private ConstraintValidatorContext context;
  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @BeforeEach
  void setUp() {
    entryValidator = new EntryAccountConsistencyValidator();
    entryValidator.initialize(null);
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
    verify(context).disableDefaultConstraintViolation();
    verify(context)
        .buildConstraintViolationWithTemplate(
            "Entry asset type FUND_UNIT does not match account asset type EUR");
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
