package ee.tuleva.onboarding.mandate.payment.rate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class PaymentRateValidator implements ConstraintValidator<ValidPaymentRate, BigDecimal> {

  private final List<BigDecimal> allowedValues =
      Arrays.asList(
          new BigDecimal("2.0"),
          new BigDecimal("4.0"),
          new BigDecimal("6.0"),
          new BigDecimal("2"),
          new BigDecimal("4"),
          new BigDecimal("6"));

  @Override
  public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return allowedValues.contains(value);
  }
}
