package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

class ThreeLetterCountryCodeValidatorSpec extends Specification {

  ThreeLetterCountryCodeValidator validator = new ThreeLetterCountryCodeValidator()

  // Locale.getISOCountries(PART3) only exposes the small set of exceptionally reserved /
  // transitional ISO 3166-1 alpha-3 codes (e.g. former countries), not the current alpha-3
  // codes for real countries such as "EST". That looks like a pre-existing production bug,
  // but fixing it is out of scope here; this test documents the validator's actual behaviour.
  def "isValid checks the code against the JDK's PART3 ISO country code set"() {
    expect:
    validator.isValid("SUHH", null)
    validator.isValid("suhh", null)
    !validator.isValid("EST", null)
  }
}
