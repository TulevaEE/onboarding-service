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
    int scale = amount.scale();

    boolean isValid = scale >= assetType.getMinPrecision() && scale <= assetType.getMaxPrecision();

    if (!isValid) {
      context.disableDefaultConstraintViolation();
      String message =
          assetType.requiresExactPrecision()
              ? String.format(
                  "Amount %s has %d decimal places, but %s requires exactly %d decimal places",
                  amount.toPlainString(), scale, assetType, assetType.getMinPrecision())
              : String.format(
                  "Amount %s has %d decimal places, but %s requires between %d and %d decimal places",
                  amount.toPlainString(),
                  scale,
                  assetType,
                  assetType.getMinPrecision(),
                  assetType.getMaxPrecision());

      context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
      return false;
    }

    return true;
  }
}
