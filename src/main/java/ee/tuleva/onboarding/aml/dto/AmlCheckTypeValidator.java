package ee.tuleva.onboarding.aml.dto;

import ee.tuleva.onboarding.aml.AmlCheckType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AmlCheckTypeValidator implements ConstraintValidator<ValidAmlCheckType, AmlCheckType> {

  @Override
  public boolean isValid(AmlCheckType amlCheck, ConstraintValidatorContext context) {
    if (amlCheck == null) {
      return false;
    }
    return amlCheck.isManual();
  }

  @Override
  public void initialize(ValidAmlCheckType constraintAnnotation) {}
}
