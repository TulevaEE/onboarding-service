package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.validation.AmountPrecisionValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmountPrecisionValidatorTest {

  private Validator validator;
  private AmountPrecisionValidator customValidator;

  @Mock private ConstraintValidatorContext context;
  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    customValidator = new AmountPrecisionValidator();
    customValidator.initialize(null);
  }

  @Test
  @DisplayName("Should validate EUR amount with 2 decimal places")
  void shouldValidateEurWithTwoDecimals() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("100.50")).assetType(EUR).build();

    // When
    Set<ConstraintViolation<LedgerEntry>> violations = validator.validate(entry);

    // Then - Should have no precision violations
    boolean hasPrecisionViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("decimal places"));
    assertThat(hasPrecisionViolation).isFalse();
  }

  @Test
  @DisplayName("Should fail validation for EUR amount with more than 2 decimal places")
  void shouldFailEurWithMoreThanTwoDecimals() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("100.555")).assetType(EUR).build();

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains("100.555 has 3 decimal places, but EUR allows maximum 2 decimal places"));
  }

  @Test
  @DisplayName("Should validate EUR amount with no decimal places")
  void shouldValidateEurWithNoDecimals() {
    // Given
    LedgerEntry entry = LedgerEntry.builder().amount(new BigDecimal("100")).assetType(EUR).build();

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should validate FUND_UNIT amount with 5 decimal places")
  void shouldValidateFundUnitWithFiveDecimals() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("10.12345")).assetType(FUND_UNIT).build();

    // When
    Set<ConstraintViolation<LedgerEntry>> violations = validator.validate(entry);

    // Then - Should have no precision violations
    boolean hasPrecisionViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("decimal places"));
    assertThat(hasPrecisionViolation).isFalse();
  }

  @Test
  @DisplayName("Should fail validation for FUND_UNIT amount with more than 5 decimal places")
  void shouldFailFundUnitWithMoreThanFiveDecimals() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("10.123456")).assetType(FUND_UNIT).build();

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains(
                "10.123456 has 6 decimal places, but FUND_UNIT allows maximum 5 decimal places"));
  }

  @Test
  @DisplayName("Should validate FUND_UNIT amount with fewer than 5 decimal places")
  void shouldValidateFundUnitWithFewerDecimals() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("10.123")).assetType(FUND_UNIT).build();

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should handle null entry")
  void shouldHandleNullEntry() {
    // When
    boolean isValid = customValidator.isValid(null, context);

    // Then
    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  @DisplayName("Should handle entry with null amount")
  void shouldHandleNullAmount() {
    // Given
    LedgerEntry entry = LedgerEntry.builder().amount(null).assetType(EUR).build();

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should handle entry with null asset type")
  void shouldHandleNullAssetType() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("100.00")).assetType(null).build();

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should validate negative EUR amounts with correct precision")
  void shouldValidateNegativeEurAmount() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("-100.50")).assetType(EUR).build();

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Should fail negative EUR amounts with wrong precision")
  void shouldFailNegativeEurWithWrongPrecision() {
    // Given
    LedgerEntry entry =
        LedgerEntry.builder().amount(new BigDecimal("-100.555")).assetType(EUR).build();

    // Setup mocks
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    // When
    boolean isValid = customValidator.isValid(entry, context);

    // Then
    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains("-100.555 has 3 decimal places, but EUR allows maximum 2 decimal places"));
  }

  @Test
  @DisplayName("Should test LedgerAccount addEntry validation")
  void shouldValidateAddEntryAssetTypeMismatch() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).build();

    LedgerEntry entry =
        LedgerEntry.builder()
            .amount(new BigDecimal("100.00"))
            .assetType(FUND_UNIT) // Wrong asset type
            .build();

    // When/Then
    assertThatThrownBy(() -> account.addEntry(entry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Entry asset type FUND_UNIT does not match account asset type EUR");
  }

  @Test
  @DisplayName("Should test LedgerAccount addEntry with null entry")
  void shouldFailAddNullEntry() {
    // Given
    LedgerAccount account = LedgerAccount.builder().assetType(EUR).build();

    // When/Then
    assertThatThrownBy(() -> account.addEntry(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Entry cannot be null");
  }

  private static String contains(String substring) {
    return org.mockito.ArgumentMatchers.contains(substring);
  }
}
