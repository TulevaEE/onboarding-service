package ee.tuleva.onboarding.user.address

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Specification
import spock.lang.Unroll

class AddressValidatorSpec extends Specification {

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator()

    @Unroll
    def "validates Estonian and foreign addresses"() {
        expect:
        validator.validate(address).isEmpty() == isValid
        where:
        address                         | isValid
        new Address(countryCode: "EE")  | true
        new Address(countryCode: "")    | false
        new Address(countryCode: null)  | false
        new Address(countryCode: "   ") | false
    }
}
