package ee.tuleva.onboarding.country;

import static java.util.Locale.IsoCountryCode.PART1_ALPHA2;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class Iso2CountryCodeValidator implements ConstraintValidator<ValidIso2CountryCode, String> {

  private static final Set<String> VALID_ISO2_COUNTRIES = Locale.getISOCountries(PART1_ALPHA2);

  @Override
  public boolean isValid(String countryCode, ConstraintValidatorContext context) {
    if (countryCode == null) {
      return false;
    }
    return VALID_ISO2_COUNTRIES.contains(countryCode.toUpperCase(Locale.ROOT));
  }
}
