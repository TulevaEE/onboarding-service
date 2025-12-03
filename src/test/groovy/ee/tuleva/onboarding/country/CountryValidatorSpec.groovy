package ee.tuleva.onboarding.country

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Specification
import spock.lang.Unroll

class CountryValidatorSpec extends Specification {

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator()

    @Unroll
    def "validates Estonian and foreign country codes"() {
        expect:
        validator.validate(country).isEmpty() == isValid
        where:
        country                          | isValid
        new Country(countryCode: "EE")   | true
        new Country(countryCode: "")     | false
        new Country(countryCode: null)   | false
        new Country(countryCode: "   ")  | false
    }
}
