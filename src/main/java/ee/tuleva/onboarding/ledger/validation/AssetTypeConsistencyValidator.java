package ee.tuleva.onboarding.ledger.validation;

import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class AssetTypeConsistencyValidator
    implements ConstraintValidator<AssetTypeConsistency, LedgerTransaction> {

  @Override
  public void initialize(AssetTypeConsistency constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(LedgerTransaction transaction, ConstraintValidatorContext context) {
    if (transaction == null || transaction.getEntries() == null) {
      return true; // Let @NotNull handle null cases
    }

    List<LedgerEntry> entries = transaction.getEntries();
    boolean allConsistent = true;

    // Disable default constraint violation
    context.disableDefaultConstraintViolation();

    for (LedgerEntry entry : entries) {
      // Check that entry's asset type matches its account's asset type
      if (entry.getAccount() != null && entry.getAssetType() != null) {
        if (!entry.getAssetType().equals(entry.getAccount().getAssetType())) {
          context
              .buildConstraintViolationWithTemplate(
                  String.format(
                      "Entry asset type %s does not match account asset type %s for account %s",
                      entry.getAssetType(),
                      entry.getAccount().getAssetType(),
                      entry.getAccount().getId()))
              .addConstraintViolation();
          allConsistent = false;
        }
      }
    }

    return allConsistent;
  }
}
