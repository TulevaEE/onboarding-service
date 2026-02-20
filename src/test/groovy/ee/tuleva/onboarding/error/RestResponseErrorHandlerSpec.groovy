package ee.tuleva.onboarding.error

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Specification

class RestResponseErrorHandlerSpec extends Specification {

  def objectMapper = new ObjectMapper()
  def errorHandler = new RestResponseErrorHandler(objectMapper)
  def logAppender = new ListAppender<ILoggingEvent>()
  def logger = LoggerFactory.getLogger(RestResponseErrorHandler) as Logger

  def setup() {
    logAppender.start()
    logger.addAppender(logAppender)
  }

  def cleanup() {
    logger.detachAppender(logAppender)
    logAppender.stop()
  }


  def "handles json client error responses"() {
    given:
    def response = new MockClientHttpResponse("{}".getBytes(), HttpStatus.NOT_FOUND)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    thrown(ErrorsResponseException)
  }

  def "handles json internal server error responses"() {
    given:
    def response = new MockClientHttpResponse("{}".getBytes(), HttpStatus.INTERNAL_SERVER_ERROR)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    thrown(ErrorsResponseException)
  }

  def "handles html gateway timeouts"() {
    given:
    def response = new MockClientHttpResponse("<html></html>".getBytes(), HttpStatus.GATEWAY_TIMEOUT)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    thrown(HttpServerErrorException)
  }

  def "handles non-json 4xx responses and logs response body"() {
    given:
    def htmlResponseBody = """
            <html>
            <head><title>403 Forbidden</title></head>
            <body>
            <h1>403 Forbidden</h1>
            <p>Access denied</p>
            </body>
            </html>
        """.trim()
    def response = new MockClientHttpResponse(htmlResponseBody.getBytes(), HttpStatus.FORBIDDEN)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    def exception = thrown(IOException)
    exception.message.startsWith("Failed to parse error response JSON:")
    exception.cause != null

    and: "response body is logged"
    def logEvents = logAppender.list
    logEvents.size() == 1
    def logEvent = logEvents[0]
    logEvent.level.toString() == "ERROR"
    logEvent.formattedMessage.contains("Failed to parse error response as JSON for status 403 FORBIDDEN")
    logEvent.formattedMessage.contains(htmlResponseBody)
  }

  def "handles non-json 500 responses and logs response body"() {
    given:
    def xmlResponseBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <error>
                <code>INTERNAL_ERROR</code>
                <message>Something went wrong</message>
            </error>
        """.trim()
    def response = new MockClientHttpResponse(xmlResponseBody.getBytes(), HttpStatus.INTERNAL_SERVER_ERROR)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    def exception = thrown(IOException)
    exception.message.startsWith("Failed to parse error response JSON:")
    exception.cause != null

    and: "response body is logged"
    def logEvents = logAppender.list
    logEvents.size() == 1
    def logEvent = logEvents[0]
    logEvent.level.toString() == "ERROR"
    logEvent.formattedMessage.contains("Failed to parse error response as JSON for status 500 INTERNAL_SERVER_ERROR")
    logEvent.formattedMessage.contains(xmlResponseBody)
  }

  def "handles malformed json responses and logs response body"() {
    given:
    def malformedJson = '{"error": "missing closing brace"'
    def response = new MockClientHttpResponse(malformedJson.getBytes(), HttpStatus.BAD_REQUEST)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    def exception = thrown(IOException)
    exception.message.startsWith("Failed to parse error response JSON:")
    exception.cause != null

    and: "response body is logged"
    def logEvents = logAppender.list
    logEvents.size() == 1
    def logEvent = logEvents[0]
    logEvent.level.toString() == "ERROR"
    logEvent.formattedMessage.contains("Failed to parse error response as JSON for status 400 BAD_REQUEST")
    logEvent.formattedMessage.contains(malformedJson)
  }

  def "handles empty response body and logs it"() {
    given:
    def response = new MockClientHttpResponse("".getBytes(), HttpStatus.FORBIDDEN)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    def exception = thrown(IOException)
    exception.message.startsWith("Failed to parse error response JSON:")

    and: "empty response body is logged"
    def logEvents = logAppender.list
    logEvents.size() == 1
    def logEvent = logEvents[0]
    logEvent.level.toString() == "ERROR"
    logEvent.formattedMessage.contains("Failed to parse error response as JSON for status 403 FORBIDDEN")
    logEvent.formattedMessage.contains("Response body: ")
  }

  def "logs large response bodies correctly"() {
    given:
    def largeHtmlResponse = "<html>" + "x" * 10000 + "</html>"
    def response = new MockClientHttpResponse(largeHtmlResponse.getBytes(), HttpStatus.FORBIDDEN)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    def exception = thrown(IOException)
    exception.message.startsWith("Failed to parse error response JSON:")

    and: "large response body is logged completely"
    def logEvents = logAppender.list
    logEvents.size() == 1
    def logEvent = logEvents[0]
    logEvent.level.toString() == "ERROR"
    logEvent.formattedMessage.contains("Failed to parse error response as JSON for status 403 FORBIDDEN")
    logEvent.formattedMessage.contains("<html>")
    logEvent.formattedMessage.contains("</html>")
  }

  def "does not interfere with valid json error responses"() {
    given:
    def validJsonError = '{"errors":[{"code":"INVALID_REQUEST","message":"Bad request"}]}'
    def response = new MockClientHttpResponse(validJsonError.getBytes(), HttpStatus.BAD_REQUEST)

    when:
    errorHandler.handleError(URI.create("http://test"), org.springframework.http.HttpMethod.GET, response)

    then:
    thrown(ErrorsResponseException)

    and: "no error logging occurs for valid json"
    def logEvents = logAppender.list
    logEvents.size() == 0
  }
}
