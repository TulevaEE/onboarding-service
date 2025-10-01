package ee.tuleva.onboarding.ledger.validation;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalancedTransactionValidator
    implements ConstraintValidator<BalancedTransaction, LedgerTransaction> {

  @Override
  public void initialize(BalancedTransaction constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(LedgerTransaction transaction, ConstraintValidatorContext context) {
    if (transaction == null || transaction.getEntries() == null) {
      return true; // Let @NotNull handle null cases
    }

    List<LedgerEntry> entries = transaction.getEntries();

    // Disable default constraint violation
    context.disableDefaultConstraintViolation();

    // Check minimum entry count
    if (entries.size() < 2) {
      context
          .buildConstraintViolationWithTemplate(
              "Transaction must have at least 2 entries, found: " + entries.size())
          .addConstraintViolation();
      return false;
    }

    // Check overall balance (must sum to zero)
    BigDecimal totalSum =
        entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);

    if (totalSum.compareTo(ZERO) != 0) {
      context
          .buildConstraintViolationWithTemplate(
              "Transaction entries must balance to zero. Current sum: " + totalSum)
          .addConstraintViolation();
      return false;
    }

    // Check balance per asset type
    Map<LedgerAccount.AssetType, BigDecimal> assetTypeSums = new HashMap<>();
    for (LedgerEntry entry : entries) {
      if (entry.getAssetType() != null) {
        assetTypeSums.merge(entry.getAssetType(), entry.getAmount(), BigDecimal::add);
      }
    }

    boolean allAssetTypesBalanced = true;
    for (Map.Entry<LedgerAccount.AssetType, BigDecimal> assetSum : assetTypeSums.entrySet()) {
      if (assetSum.getValue().compareTo(ZERO) != 0) {
        context
            .buildConstraintViolationWithTemplate(
                "Asset type "
                    + assetSum.getKey()
                    + " does not balance. Sum: "
                    + assetSum.getValue())
            .addConstraintViolation();
        allAssetTypesBalanced = false;
      }
    }

    return allAssetTypesBalanced;
  }
}
