package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.error.response.ErrorResponse
import ee.tuleva.onboarding.error.response.ErrorsResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.DirectFieldBindingResult
import spock.lang.Specification

class ErrorHandlingControllerAdviceSpecification extends Specification {

  def "handle ValidationErrorsException returns correct response"() {
    given: "A ValidationErrorsException with a DirectFieldBindingResult"
        def bindingResult = new DirectFieldBindingResult("", "")
        bindingResult.reject("error.code", "Default message")
        def exception = new ValidationErrorsException(bindingResult)
        def advice = new ErrorHandlingControllerAdvice()
        def errorsResponse = new ErrorsResponse([
            ErrorResponse.builder().code("error.code").message("Default message").build()
        ])
        def expectedResponseEntity = new ResponseEntity<>(errorsResponse, HttpStatus.BAD_REQUEST)

    when: "handleErrors is invoked with ValidationErrorsException"
        def result = advice.handleErrors(exception)

    then: "The errorResponseEntityFactory returns the expected response"
        result.statusCode == expectedResponseEntity.statusCode
        result.body == expectedResponseEntity.body
  }

  def "handle generic ErrorsResponseException returns BAD_REQUEST response"() {
    given: "An instance of ErrorHandlingControllerAdvice and a generic ErrorsResponseException"
        def advice = new ErrorHandlingControllerAdvice()
        def errorsResponse = new ErrorsResponse([])
        def exception = new ErrorsResponseException(errorsResponse)

    when: "handleErrors is invoked with ErrorsResponseException"
        def result = advice.handleErrors(exception)

    then: "The response is BAD_REQUEST with the exception's ErrorsResponse"
        result.statusCode == HttpStatus.BAD_REQUEST
        result.body == errorsResponse
  }
}
