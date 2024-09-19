package ee.tuleva.onboarding.epis.mandate.details;

import static java.util.Locale.IsoCountryCode.PART3;

import java.util.Locale;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ThreeLetterCountryCodeValidator
    implements ConstraintValidator<Valid3LetterCountryCode, String> {

  private static final Set<String> VALID_ISO3_COUNTRIES = Locale.getISOCountries(PART3);

  @Override
  public boolean isValid(String countryCode, ConstraintValidatorContext context) {
    return VALID_ISO3_COUNTRIES.contains(countryCode.toUpperCase());
  }
}
