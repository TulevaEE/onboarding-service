package ee.tuleva.onboarding.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that a ledger transaction is balanced (entries sum to zero). This ensures double-entry
 * bookkeeping integrity.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BalancedTransactionValidator.class)
@Documented
public @interface BalancedTransaction {
  String message() default "Transaction entries must sum to zero for double-entry bookkeeping";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
