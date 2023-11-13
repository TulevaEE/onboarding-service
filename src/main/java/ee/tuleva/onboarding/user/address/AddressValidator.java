package ee.tuleva.onboarding.user.address;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class AddressValidator implements ConstraintValidator<ValidAddress, Address> {

  @Override
  public boolean isValid(Address address, ConstraintValidatorContext context) {
    return isNotBlank(address.getCountryCode());
  }

  @Override
  public void initialize(ValidAddress constraintAnnotation) {}
}
