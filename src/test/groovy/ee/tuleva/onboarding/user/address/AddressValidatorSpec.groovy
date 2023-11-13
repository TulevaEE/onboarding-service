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
        address                         | isValid
        new Address(countryCode: "EE")  | true
        new Address(countryCode: "")    | false
    }
}
