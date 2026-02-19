package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
  void shouldValidateWhenAssetTypesMatch() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);

    transaction.addEntry(eurAccount1, new BigDecimal("-100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("100.00"));

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    boolean hasAssetTypeViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("asset type"));
    assertThat(hasAssetTypeViolation).isFalse();
  }

  @Test
  void shouldFailWhenAssetTypeMismatch() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount = createAccount(EUR, ASSET);
    LedgerAccount fundAccount = createAccount(FUND_UNIT, ASSET);

    LedgerEntry entry1 =
        LedgerEntry.builder()
            .account(eurAccount)
            .amount(new BigDecimal("-100.00"))
            .assetType(FUND_UNIT)
            .transaction(transaction)
            .build();

    LedgerEntry entry2 =
        LedgerEntry.builder()
            .account(fundAccount)
            .amount(new BigDecimal("100.00"))
            .assetType(EUR)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry1);
    transaction.getEntries().add(entry2);

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
    verify(context, times(2)).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  void shouldHandleNullTransaction() {
    boolean isValid = customValidator.isValid(null, context);

    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  void shouldHandleNullEntries() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isTrue();
  }

  @Test
  void shouldValidateMixedAssetTypes() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);
    LedgerAccount fundAccount1 = createAccount(FUND_UNIT, ASSET);
    LedgerAccount fundAccount2 = createAccount(FUND_UNIT, LIABILITY);

    transaction.addEntry(eurAccount1, new BigDecimal("100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("-100.00"));
    transaction.addEntry(fundAccount1, new BigDecimal("10.0000"));
    transaction.addEntry(fundAccount2, new BigDecimal("-10.0000"));

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    boolean hasAssetTypeViolation =
        violations.stream().anyMatch(v -> v.getMessage().contains("asset type"));
    assertThat(hasAssetTypeViolation).isFalse();
  }

  @Test
  void shouldThrowExceptionForNullAccount() {
    LedgerTransaction transaction = createTransaction();

    assertThatThrownBy(() -> transaction.addEntry(null, new BigDecimal("100.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowExceptionForNullAmount() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account = createAccount(EUR, ASSET);

    assertThatThrownBy(() -> transaction.addEntry(account, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAutoSetAssetTypeFromAccount() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount = createAccount(EUR, ASSET);
    LedgerAccount fundAccount = createAccount(FUND_UNIT, LIABILITY);

    LedgerEntry eurEntry = transaction.addEntry(eurAccount, new BigDecimal("100.00"));
    LedgerEntry fundEntry = transaction.addEntry(fundAccount, new BigDecimal("-10.0000"));

    assertThat(eurEntry.getAssetType()).isEqualTo(EUR);
    assertThat(fundEntry.getAssetType()).isEqualTo(FUND_UNIT);
  }

  private LedgerTransaction createTransaction() {
    return LedgerTransaction.builder()
        .transactionType(ADJUSTMENT)
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
