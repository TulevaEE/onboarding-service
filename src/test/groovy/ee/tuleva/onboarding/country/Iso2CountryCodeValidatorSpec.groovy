package ee.tuleva.onboarding.country

import spock.lang.Specification
import spock.lang.Unroll

class Iso2CountryCodeValidatorSpec extends Specification {

    Iso2CountryCodeValidator validator = new Iso2CountryCodeValidator()

    @Unroll
    def "validates ISO2 country code #countryCode as #expected"() {
        expect:
        validator.isValid(countryCode, null) == expected

        where:
        countryCode | expected
        "EE"        | true
        "ee"        | true
        "US"        | true
        "XX"        | false
        "EST"       | false
        null        | false
    }
}
