package ee.tuleva.onboarding.ledger.validation;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class AmountPrecisionValidator
    implements ConstraintValidator<ValidAmountPrecision, LedgerEntry> {

  @Override
  public void initialize(ValidAmountPrecision constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(LedgerEntry entry, ConstraintValidatorContext context) {
    if (entry == null || entry.getAmount() == null || entry.getAssetType() == null) {
      return true; // Let @NotNull handle null cases
    }

    BigDecimal amount = entry.getAmount();
    LedgerAccount.AssetType assetType = entry.getAssetType();

    // Get the scale (number of decimal places)
    int scale = amount.scale();

    // Check based on asset type
    boolean isValid;
    int maxScale;

    switch (assetType) {
      case EUR:
        maxScale = 2;
        isValid = scale <= 2;
        break;
      case FUND_UNIT:
        maxScale = 5;
        isValid = scale <= 5;
        break;
      default:
        return true; // Unknown asset type, let it pass
    }

    if (!isValid) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              String.format(
                  "Amount %s has %d decimal places, but %s allows maximum %d decimal places",
                  amount.toPlainString(), scale, assetType, maxScale))
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
