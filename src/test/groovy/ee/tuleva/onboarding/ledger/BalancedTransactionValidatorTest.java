package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
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
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
  void shouldValidateBalancedTransactionWithTwoEntries() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount fromAccount = createAccount(EUR, ASSET);
    LedgerAccount toAccount = createAccount(EUR, ASSET);

    BigDecimal amount = new BigDecimal("100.00");
    transaction.addEntry(fromAccount, amount.negate());
    transaction.addEntry(toAccount, amount);

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailValidationWhenNotBalanced() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, ASSET);

    transaction.addEntry(account1, new BigDecimal("100.00"));
    transaction.addEntry(account2, new BigDecimal("-50.00"));

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(
            contains("Transaction entries must balance to zero. Current sum: 50"));
  }

  @Test
  void shouldFailValidationWithOnlyOneEntry() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account = createAccount(EUR, ASSET);
    transaction.addEntry(account, new BigDecimal("100.00"));

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
  }

  @Test
  void shouldFailValidationWithNoEntries() {
    LedgerTransaction transaction = createTransaction();

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
  }

  @Test
  void shouldValidateComplexMultiEntryTransaction() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);
    LedgerAccount account4 = createAccount(EUR, LIABILITY);

    transaction.addEntry(account1, new BigDecimal("500.00"));
    transaction.addEntry(account2, new BigDecimal("200.00"));
    transaction.addEntry(account3, new BigDecimal("-300.00"));
    transaction.addEntry(account4, new BigDecimal("-400.00"));

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailValidationWhenDifferentAssetTypesNotBalanced() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount eurAccount1 = createAccount(EUR, ASSET);
    LedgerAccount eurAccount2 = createAccount(EUR, LIABILITY);
    LedgerAccount fundAccount1 = createAccount(FUND_UNIT, ASSET);
    LedgerAccount fundAccount2 = createAccount(FUND_UNIT, LIABILITY);

    transaction.addEntry(eurAccount1, new BigDecimal("100.00"));
    transaction.addEntry(eurAccount2, new BigDecimal("-100.00"));
    transaction.addEntry(fundAccount1, new BigDecimal("10.0000"));
    transaction.addEntry(fundAccount2, new BigDecimal("-5.0000"));

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
    verify(context, atLeastOnce()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  void shouldValidateWhenMultipleAssetTypesBalanceIndividually() {
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

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldHandleNullTransaction() {
    boolean isValid = customValidator.isValid(null, context);

    assertThat(isValid).isTrue();
    verify(context, never()).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  void shouldHandleTransactionWithNullEntries() {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
  }

  @Test
  void shouldValidateTransactionWithExactlyZeroSum() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);

    transaction.addEntry(account1, new BigDecimal("0.01"));
    transaction.addEntry(account2, new BigDecimal("-0.01"));

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailValidationWithPrecisionRoundingErrors() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);

    BigDecimal total = new BigDecimal("100.00");
    BigDecimal third = new BigDecimal("33.33");

    transaction.addEntry(account1, third);
    transaction.addEntry(account2, third);
    transaction.addEntry(account3, third);
    transaction.addEntry(account1, total.negate());

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    when(violationBuilder.addConstraintViolation()).thenReturn(context);

    boolean isValid = customValidator.isValid(transaction, context);

    assertThat(isValid).isFalse();
    verify(context)
        .buildConstraintViolationWithTemplate(contains("Transaction entries must balance to zero"));
  }

  @Test
  void shouldValidateTransactionWithProperPrecisionHandling() {
    LedgerTransaction transaction = createTransaction();
    LedgerAccount account1 = createAccount(EUR, ASSET);
    LedgerAccount account2 = createAccount(EUR, LIABILITY);
    LedgerAccount account3 = createAccount(EUR, ASSET);
    LedgerAccount account4 = createAccount(EUR, LIABILITY);

    transaction.addEntry(account1, new BigDecimal("33.34"));
    transaction.addEntry(account2, new BigDecimal("33.33"));
    transaction.addEntry(account3, new BigDecimal("33.33"));
    transaction.addEntry(account4, new BigDecimal("-100.00"));

    Set<ConstraintViolation<LedgerTransaction>> violations = validator.validate(transaction);

    assertThat(violations).isEmpty();
  }

  private LedgerTransaction createTransaction() {
    return LedgerTransaction.builder()
        .transactionType(ADJUSTMENT)
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
}
