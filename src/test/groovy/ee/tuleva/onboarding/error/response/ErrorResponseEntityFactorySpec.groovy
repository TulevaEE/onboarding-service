package ee.tuleva.onboarding.error.response


import ee.tuleva.onboarding.error.converter.TestCommand
import org.springframework.http.ResponseEntity
import org.springframework.validation.Errors
import spock.lang.Specification

import static org.springframework.http.HttpStatus.BAD_REQUEST

class ErrorResponseEntityFactorySpec extends Specification {

    ErrorResponseEntityFactory errorResponseEntityFactory = new ErrorResponseEntityFactory()

    def "OfErrors: Creates errors response from errors"() {
        given:
        Errors errors = ErrorFactory.manufactureErrors(new TestCommand())
        when:
        ResponseEntity responseEntity = errorResponseEntityFactory.fromErrors(errors)

        then:
        responseEntity.statusCode == BAD_REQUEST
        responseEntity.body.errors == []
    }
}
