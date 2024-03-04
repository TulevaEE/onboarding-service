package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import org.springframework.validation.DirectFieldBindingResult
import spock.lang.Specification
import org.springframework.http.HttpStatus
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import ee.tuleva.onboarding.error.response.ErrorsResponse
import org.springframework.http.ResponseEntity
import ee.tuleva.onboarding.mandate.exception.IdSessionException
import io.jsonwebtoken.ExpiredJwtException
import ee.tuleva.onboarding.auth.ExpiredRefreshJwtException

class ErrorHandlingControllerAdviceSpecification extends Specification {

  def "handle ValidationErrorsException returns correct response"() {
    given: "A ValidationErrorsException with a DirectFieldBindingResult"
        def bindingResult = new DirectFieldBindingResult("", "")
        bindingResult.reject("error.code", "Default message")
        def exception = new ValidationErrorsException(bindingResult)
        def errorResponseEntityFactory = Mock(ErrorResponseEntityFactory)
        def advice = new ErrorHandlingControllerAdvice(errorResponseEntityFactory)
        def errorsResponse = new ErrorsResponse(bindingResult.allErrors.collect { it.defaultMessage })
        def expectedResponseEntity = new ResponseEntity<>(errorsResponse, HttpStatus.OK)
        errorResponseEntityFactory.fromErrors(exception.errors) >> expectedResponseEntity

    when: "handleErrors is invoked with ValidationErrorsException"
        def result = advice.handleErrors(exception)

    then: "The errorResponseEntityFactory is called with correct errors and returns the expected response"
        1 * errorResponseEntityFactory.fromErrors(exception.errors) >> expectedResponseEntity
        result == expectedResponseEntity
  }


  def "handle IdSessionException returns UNAUTHORIZED response"() {
    given: "An IdSessionException with ErrorsResponse"
        def exception = new IdSessionException(new ErrorsResponse([]))

        def advice = new ErrorHandlingControllerAdvice(null)

    when: "handleErrors is invoked with IdSessionException"
        def result = advice.handleErrors(exception)

    then: "The response is UNAUTHORIZED with correct body"
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == exception.errorsResponse
  }

  def "handle generic ErrorsResponseException returns BAD_REQUEST response"() {
    given: "An instance of ErrorHandlingControllerAdvice and a generic ErrorsResponseException"
        def advice = new ErrorHandlingControllerAdvice(null)
        def errorsResponse = new ErrorsResponse([])
        def exception = new ErrorsResponseException(errorsResponse)

    when: "handleErrors is invoked with ErrorsResponseException"
        def result = advice.handleErrors(exception)

    then: "The response is BAD_REQUEST with the exception's ErrorsResponse"
        result.statusCode == HttpStatus.BAD_REQUEST
        result.body == errorsResponse
  }

  def "handle AuthNotCompleteException returns OK response with specific message"() {
    given: "An instance of ErrorHandlingControllerAdvice and an AuthNotCompleteException"
        def advice = new ErrorHandlingControllerAdvice(null) // No dependencies needed for this test
        def exception = new AuthNotCompleteException()

    when: "handleErrors is invoked with AuthNotCompleteException"
        def result = advice.handleErrors(exception)

    then: "The response is OK with specific error details"
        result.statusCode == HttpStatus.OK
        result.body == [error: "AUTHENTICATION_NOT_COMPLETE", error_description: "Please keep polling."]
  }

  def "handle ExpiredJwtException returns UNAUTHORIZED response with expected error details"() {
    given: "An ExpiredJwtException"
        def exception = new ExpiredJwtException(null, null, "The token is expired.")

        def advice = new ErrorHandlingControllerAdvice(null)

    when: "handleErrors is invoked with ExpiredJwtException"
        def result = advice.handleErrors(exception)

    then: "The response is UNAUTHORIZED with correct error details"
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == [error: "TOKEN_EXPIRED", error_description: "The token is expired."]
  }

  def "handle ExpiredRefreshJwtException returns FORBIDDEN response with expected error details"() {
    given: "An instance of ErrorHandlingControllerAdvice and an ExpiredRefreshJwtException"
        def advice = new ErrorHandlingControllerAdvice(null)
        def exception = new ExpiredRefreshJwtException()

    when: "handleErrors is invoked with ExpiredRefreshJwtException"
        def result = advice.handleErrors(exception)

    then: "The response is FORBIDDEN with correct error details"
        result.statusCode == HttpStatus.FORBIDDEN
        result.body == [error: "REFRESH_TOKEN_EXPIRED", error_description: "The refresh token is expired."]
  }
}
