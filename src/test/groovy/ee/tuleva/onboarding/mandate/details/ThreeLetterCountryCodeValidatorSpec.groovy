package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

class ThreeLetterCountryCodeValidatorSpec extends Specification {

  ThreeLetterCountryCodeValidator validator = new ThreeLetterCountryCodeValidator()

  def "accepts current ISO 3166-1 alpha-3 codes case-insensitively"() {
    expect:
    validator.isValid("EST", null)
    validator.isValid("est", null)
    validator.isValid("USA", null)
    validator.isValid("FIN", null)
  }

  def "rejects codes outside the current alpha-3 set"() {
    expect:
    !validator.isValid("SUHH", null)
    !validator.isValid("XYZ", null)
    !validator.isValid("E", null)
    !validator.isValid("", null)
  }

  def "treats null as valid so presence is a separate constraint"() {
    expect:
    validator.isValid(null, null)
  }
}
