package ee.tuleva.onboarding.ledger.validation;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class AccountEntryConsistencyValidator
    implements ConstraintValidator<AccountEntryConsistency, LedgerAccount> {

  @Override
  public void initialize(AccountEntryConsistency constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(LedgerAccount account, ConstraintValidatorContext context) {
    if (account == null || account.getEntries() == null || account.getAssetType() == null) {
      return true; // Let @NotNull handle null cases
    }

    List<LedgerEntry> entries = account.getEntries();
    LedgerAccount.AssetType accountAssetType = account.getAssetType();
    boolean allConsistent = true;

    // Disable default constraint violation
    context.disableDefaultConstraintViolation();

    for (LedgerEntry entry : entries) {
      if (entry.getAssetType() != null && !entry.getAssetType().equals(accountAssetType)) {
        context
            .buildConstraintViolationWithTemplate(
                String.format(
                    "Entry with ID %s has asset type %s which doesn't match account asset type %s",
                    entry.getId(), entry.getAssetType(), accountAssetType))
            .addConstraintViolation();
        allConsistent = false;
      }
    }

    return allConsistent;
  }
}
