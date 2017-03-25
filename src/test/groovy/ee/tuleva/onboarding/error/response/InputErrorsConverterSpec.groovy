package ee.tuleva.onboarding.error.response

import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import spock.lang.Specification

class InputErrorsConverterSpec extends Specification {

    InputErrorsConverter errorsConverter = new InputErrorsConverter()

    void setup() {
    }

    def "converts global errors"() {
        when:
        Errors errors = createErrorsFor(new Object())
        def errorCode = "errorCode"
        def args = ["args1", "args2"]
        errors.reject(errorCode, args as String[], "default message")

        then:
        def errorsResponse = errorsConverter.convert(errors)
        errorsResponse.getErrors().size() == 1
        errorsResponse.getErrors()[0].code == errorCode
        errorsResponse.getErrors()[0].arguments == args
        !errorsResponse.getErrors()[0].path
        errorsResponse.getErrors()[0].message == "default message"
    }

    def "converts field errors"() {
        when:
        Errors errors = createErrorsFor(new TestCommand())
        def errorCode = "errorCode"
        def args = ["args1", "args2"]
        def field = "someField"
        errors.rejectValue(field, errorCode, args as String[], "default message")

        then:
        def errorsResponse = errorsConverter.convert(errors)
        errorsResponse.getErrors().size() == 1
        errorsResponse.getErrors()[0].code == errorCode
        errorsResponse.getErrors()[0].path == field
        errorsResponse.getErrors()[0].arguments == args
        errorsResponse.getErrors()[0].message == "default message"
    }

    def "returns converted field errors and global errors as single list"() {
        Errors errors = createErrorsFor(new TestCommand())
        def errorCode = "errorCode"

        errors.reject(errorCode, "default message")
        errors.rejectValue("someField", errorCode, "default message")

        expect:
        errorsConverter.convert(errors).getErrors().size() == 2
    }

    static Errors createErrorsFor(Object target) {
        return ErrorFactory.manufactureErrors(target);
    }

}

class TestCommand {
    String someField
}
