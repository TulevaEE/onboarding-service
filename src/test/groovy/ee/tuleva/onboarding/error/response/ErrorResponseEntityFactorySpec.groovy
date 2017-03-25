package ee.tuleva.onboarding.error.response

import org.springframework.http.ResponseEntity
import org.springframework.validation.Errors
import spock.lang.Specification

import static org.springframework.http.HttpStatus.BAD_REQUEST

class ErrorResponseEntityFactorySpec extends Specification {

    InputErrorsConverter inputErrorsConverter = Mock(InputErrorsConverter)
    ErrorResponseEntityFactory errorResponseEntityFactory = new ErrorResponseEntityFactory(inputErrorsConverter)

    def "OfErrors: Creates errors response from errors"() {
        given:
        Errors errors = ErrorFactory.manufactureErrors(new TestCommand())
        1 * inputErrorsConverter.convert(errors) >> new ErrorsResponse()
        when:
        ResponseEntity responseEntity = errorResponseEntityFactory.fromErrors(errors)

        then:
        responseEntity.statusCode == BAD_REQUEST
        responseEntity.body.errors == null
    }
}