package ee.tuleva.onboarding.user.address;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AddressValidator implements ConstraintValidator<ValidAddress, Address> {

    @Override
    public boolean isValid(Address address, ConstraintValidatorContext context) {
        return isNotBlank(address.getStreet()) &&
            isNotBlank(address.getPostalCode()) &&
            (address.isEstonian() && isNotBlank(address.getDistrictCode()) ||
                isNotBlank(address.getCountryCode()) && !address.isEstonian());
    }

    @Override
    public void initialize(ValidAddress constraintAnnotation) {
    }
}
