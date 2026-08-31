package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException
import ee.tuleva.onboarding.signature.IdSessionException
import org.springframework.http.HttpStatus
import spock.lang.Specification

class MandateErrorHandlerSpec extends Specification {

  MandateErrorHandler handler = new MandateErrorHandler()

  def "handle IdSessionException returns UNAUTHORIZED response"() {
    given:
        def exception = new IdSessionException(new ErrorsResponse([]))

    when:
        def result = handler.handleErrors(exception)

    then:
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == exception.errorsResponse
  }

  def "handle InvalidMandateException returns BAD_REQUEST response"() {
    given:
        def exception = new InvalidMandateException(new ErrorsResponse([]))

    when:
        def result = handler.handleErrors(exception)

    then:
        result.statusCode == HttpStatus.BAD_REQUEST
        result.body == exception.errorsResponse
  }

  def "handle MandateProcessingException returns INTERNAL_SERVER_ERROR response"() {
    given:
        def exception = new MandateProcessingException(new ErrorsResponse([]))

    when:
        def result = handler.handleErrors(exception)

    then:
        result.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        result.body == exception.errorsResponse
  }
}
