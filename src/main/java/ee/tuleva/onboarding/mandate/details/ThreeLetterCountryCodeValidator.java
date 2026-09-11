package ee.tuleva.onboarding.mandate.details;

import static java.util.Locale.IsoCountryCode.PART1_ALPHA3;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class ThreeLetterCountryCodeValidator
    implements ConstraintValidator<Valid3LetterCountryCode, String> {

  private static final Set<String> VALID_ISO3_COUNTRIES = Locale.getISOCountries(PART1_ALPHA3);

  @Override
  public boolean isValid(String countryCode, ConstraintValidatorContext context) {
    return countryCode == null || VALID_ISO3_COUNTRIES.contains(countryCode.toUpperCase());
  }
}
