package ee.tuleva.onboarding.error

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Specification

class RestResponseErrorHandlerSpec extends Specification {

    def objectMapper = new ObjectMapper()
    def errorHandler = new RestResponseErrorHandler(objectMapper)


    def "handles json client error responses"() {
        given:
        def response = new MockClientHttpResponse("{}".getBytes(), HttpStatus.NOT_FOUND)

        when:
        errorHandler.handleError(response)

        then:
        thrown(ErrorsResponseException)
    }

    def "handles json internal server error responses"() {
        given:
        def response = new MockClientHttpResponse("{}".getBytes(), HttpStatus.INTERNAL_SERVER_ERROR)

        when:
        errorHandler.handleError(response)

        then:
        thrown(ErrorsResponseException)
    }

    def "handles html gateway timeouts"() {
        given:
        def response = new MockClientHttpResponse("<html></html>".getBytes(), HttpStatus.GATEWAY_TIMEOUT)

        when:
        errorHandler.handleError(response)

        then:
        thrown(HttpServerErrorException)
    }
}
