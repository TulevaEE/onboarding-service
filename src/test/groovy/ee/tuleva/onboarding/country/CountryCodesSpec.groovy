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

    @Unroll
    def "converts ISO 3166-1 numeric country code #input to alpha-2 #expected"() {
        expect:
        CountryCodes.numericToAlpha2(input) == expected
        where:
        input   | expected
        "233"   | "EE"
        "578"   | "NO"
        "643"   | "RU"
        "408"   | "KP"
        "826"   | "GB"
        "004"   | "AF"
        "4"     | "AF"
        " 233 " | "EE"
        null    | null
        ""      | null
        "   "   | null
        "999"   | "999"
        "EE"    | "EE"
    }

    @Unroll
    def "sanctions-critical numeric #input maps to exactly #expected"() {
        expect:
        CountryCodes.numericToAlpha2(input) == expected
        where:
        input | expected
        "643" | "RU"
        "408" | "KP"
        "364" | "IR"
        "760" | "SY"
        "112" | "BY"
        "192" | "CU"
        "104" | "MM"
        "004" | "AF"
        "862" | "VE"
        "156" | "CN"
        "784" | "AE"
        "729" | "SD"
        "728" | "SS"
        "233" | "EE"
        "352" | "IS"
        "438" | "LI"
        "578" | "NO"
    }

    def "every AML high-risk and EEA country used by the risk views is mapped"() {
        given:
        def required = [
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "IS", "LI", "NO",
            "AF", "DZ", "AO", "BY", "BA", "BF", "BI", "CM", "CF", "CN", "CU", "CD", "EG", "GT",
            "GN", "GW", "HT", "IR", "IQ", "JO", "KG", "LB", "LY", "ML", "ME", "MA", "MZ", "MM",
            "NI", "NE", "NG", "KP", "PK", "PS", "RU", "RS", "SO", "SS", "SD", "SY", "TJ", "TN",
            "TM", "AE", "VE", "YE", "ZW"
        ] as Set

        expect:
        CountryCodes.mappedAlpha2Codes().containsAll(required)
    }
}
