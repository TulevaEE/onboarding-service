package ee.tuleva.onboarding.kyb;

import static org.apache.commons.lang3.StringUtils.isBlank;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RegistryCodeValidator implements ConstraintValidator<ValidRegistryCode, String> {

  public boolean isValid(String registryCode) {
    return isValid(registryCode, null);
  }

  @Override
  public boolean isValid(String registryCode, ConstraintValidatorContext context) {
    if (isBlank(registryCode)) {
      return false;
    }
    if (registryCode.length() != 8) {
      return false;
    }
    return registryCode.chars().allMatch(Character::isDigit);
  }

  @Override
  public void initialize(ValidRegistryCode constraintAnnotation) {}
}
