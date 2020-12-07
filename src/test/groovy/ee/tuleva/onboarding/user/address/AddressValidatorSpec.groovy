package ee.tuleva.onboarding.user.address

import spock.lang.Specification
import spock.lang.Unroll

class AddressValidatorSpec extends Specification {

    AddressValidator addressValidator = new AddressValidator()

    @Unroll
    def "validates Estonian and foreign addresses"() {
        expect:
        addressValidator.isValid(address, null) == isValid
        where:
        address                                                                                            | isValid
        new Address(street: "Telliskivi 60", districtCode: "0037", postalCode: "10149", countryCode: "EE") | true
        new Address(street: null, districtCode: "0037", postalCode: "10149", countryCode: "EE")            | false
        new Address(street: "Telliskivi 60", districtCode: null, postalCode: "10149", countryCode: "EE")   | false
        new Address(street: "Telliskivi 60", districtCode: "0037", postalCode: null, countryCode: "EE")    | false
        new Address(street: "Telliskivi 60", districtCode: "0037", postalCode: "10149", countryCode: null) | false
        new Address(street: "Abbey Road 3", districtCode: null, postalCode: "NW8 9AY", countryCode: "UK")  | true
    }
}
