package ee.tuleva.onboarding.country

import spock.lang.Specification
import spock.lang.Unroll

class CountryCodesSpec extends Specification {

    @Unroll
    def "converts alpha-3 country code #input to alpha-2 #expected"() {
        expect:
        CountryCodes.toAlpha2(input) == expected
        where:
        input   | expected
        "EST"   | "EE"
        "DEU"   | "DE"
        "GBR"   | "GB"
        "est"   | "EE"
        null    | null
        ""      | null
        "   "   | null
        "XXX"   | "XXX"
        "EE"    | "EE"
    }
}
