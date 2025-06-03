package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.auth.ExpiredRefreshJwtException
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.error.response.ErrorResponse
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.exception.IdSessionException
import io.jsonwebtoken.ExpiredJwtException
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

  def "handle IdSessionException returns UNAUTHORIZED response"() {
    given: "An IdSessionException with ErrorsResponse"
        def exception = new IdSessionException(new ErrorsResponse([]))

        def advice = new ErrorHandlingControllerAdvice()

    when: "handleErrors is invoked with IdSessionException"
        def result = advice.handleErrors(exception)

    then: "The response is UNAUTHORIZED with correct body"
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == exception.errorsResponse
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

  def "handle AuthNotCompleteException returns OK response with specific message"() {
    given: "An instance of ErrorHandlingControllerAdvice and an AuthNotCompleteException"
        def advice = new ErrorHandlingControllerAdvice() // No dependencies needed for this test
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

        def advice = new ErrorHandlingControllerAdvice()

    when: "handleErrors is invoked with ExpiredJwtException"
        def result = advice.handleErrors(exception)

    then: "The response is UNAUTHORIZED with correct error details"
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == [error: "TOKEN_EXPIRED", error_description: "The token is expired."]
  }

  def "handle ExpiredRefreshJwtException returns FORBIDDEN response with expected error details"() {
    given: "An instance of ErrorHandlingControllerAdvice and an ExpiredRefreshJwtException"
        def advice = new ErrorHandlingControllerAdvice()
        def exception = new ExpiredRefreshJwtException()

    when: "handleErrors is invoked with ExpiredRefreshJwtException"
        def result = advice.handleErrors(exception)

    then: "The response is FORBIDDEN with correct error details"
        result.statusCode == HttpStatus.FORBIDDEN
        result.body == [error: "REFRESH_TOKEN_EXPIRED", error_description: "The refresh token is expired."]
  }
}
