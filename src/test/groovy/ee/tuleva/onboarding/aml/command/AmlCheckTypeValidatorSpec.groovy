package ee.tuleva.onboarding.aml.command

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.aml.AmlCheckType.*

class AmlCheckTypeValidatorSpec extends Specification {

    AmlCheckTypeValidator validator = new AmlCheckTypeValidator()

    @Unroll
    def 'validates aml check type code: "#checkType"'() {
        when:
        def response = validator.isValid(checkType, null)
        then:
        response == isValid
        where:
        checkType                  | isValid
        null                       | false
        RESIDENCY_MANUAL           | true
        POLITICALLY_EXPOSED_PERSON | true
        RESIDENCY_AUTO             | false
        DOCUMENT                   | false
        PENSION_REGISTRY_NAME      | false
        SK_NAME                    | false
    }
}
