package ee.tuleva.onboarding.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that the amount precision matches the asset type requirements: - EUR: 0-2 decimal
 * places - FUND_UNIT: exactly 5 decimal places
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AmountPrecisionValidator.class)
@Documented
public @interface ValidAmountPrecision {
  String message() default
      "Amount precision must match asset type (EUR: 0-2 decimals, FUND_UNIT: exactly 5 decimals)";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
