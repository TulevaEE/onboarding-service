package ee.tuleva.onboarding.party;

import static org.apache.commons.lang3.StringUtils.isBlank;

import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PartyCodeValidator implements ConstraintValidator<ValidPartyCode, String> {

  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();
  private static final Pattern REGISTRY_CODE_PATTERN = Pattern.compile("\\d{8}");

  @Override
  public boolean isValid(String code, ConstraintValidatorContext context) {
    if (isBlank(code)) {
      return false;
    }
    return personalCodeValidator.isValid(code) || REGISTRY_CODE_PATTERN.matcher(code).matches();
  }
}
