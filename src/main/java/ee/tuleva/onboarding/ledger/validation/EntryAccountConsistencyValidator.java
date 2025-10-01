package ee.tuleva.onboarding.ledger.validation;

import ee.tuleva.onboarding.ledger.LedgerEntry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EntryAccountConsistencyValidator
    implements ConstraintValidator<EntryAccountConsistency, LedgerEntry> {

  @Override
  public void initialize(EntryAccountConsistency constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(LedgerEntry entry, ConstraintValidatorContext context) {
    if (entry == null || entry.getAccount() == null || entry.getAssetType() == null) {
      return true; // Let @NotNull handle null cases
    }

    // Check if entry's asset type matches its account's asset type
    if (entry.getAccount().getAssetType() != null
        && !entry.getAssetType().equals(entry.getAccount().getAssetType())) {

      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              String.format(
                  "Entry asset type %s does not match account asset type %s",
                  entry.getAssetType(), entry.getAccount().getAssetType()))
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
