package ee.tuleva.onboarding.aml.command;

import ee.tuleva.onboarding.aml.AmlCheckType;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class AmlCheckTypeValidator implements ConstraintValidator<ValidAmlCheckType, AmlCheckType> {

    @Override
    public boolean isValid(AmlCheckType amlCheck, ConstraintValidatorContext context) {
        if (amlCheck == null) {
            return false;
        }
        return amlCheck.isManual();
    }


    @Override
    public void initialize(ValidAmlCheckType constraintAnnotation) {
    }
}
