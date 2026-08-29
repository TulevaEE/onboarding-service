package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.error.response.ErrorsResponse
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
}
