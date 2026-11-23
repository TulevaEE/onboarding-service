package ee.tuleva.onboarding.investment.check.health

import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.HttpHostConnectException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification
import spock.lang.Unroll

import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.NOT_TAKEN
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.NO_ANSWER
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.PAYLOAD_REFUSED
import static java.nio.charset.StandardCharsets.UTF_8
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS

class SlackPostFailureSpec extends Specification {

  @Unroll
  def "aFailedPostIs #expected when #what"() {
    expect:
    SlackPostFailure.of(failure) == expected

    where:
    what                                             | failure                                                            || expected
    "the answer timed out after the request went out" | io(new SocketTimeoutException("Read timed out"))                   || NO_ANSWER
    "the JDK client's request timed out"             | io(new HttpTimeoutException("request timed out"))                  || NO_ANSWER
    "the connection timed out"                       | io(new ConnectTimeoutException("Connect timed out"))               || NOT_TAKEN
    "the JDK client's connection timed out"          | io(new HttpConnectTimeoutException("HTTP connect timed out"))      || NOT_TAKEN
    "a plain socket's connect timed out"             | io(new SocketTimeoutException("Connect timed out"))                || NOT_TAKEN
    "the connection was refused"                     | io(new HttpHostConnectException("Connection refused"))             || NOT_TAKEN
    "the host is unknown"                            | io(new UnknownHostException("hooks.slack.com"))                    || NOT_TAKEN
    "Slack names an invalid payload"                 | slack(BAD_REQUEST, "invalid_payload")                              || PAYLOAD_REFUSED
    "Slack says the post has no text"                | slack(BAD_REQUEST, "no_text")                                      || PAYLOAD_REFUSED
    "Slack says it has too many attachments"         | slack(BAD_REQUEST, "too_many_attachments")                         || PAYLOAD_REFUSED
    "Slack says the payload is too large"            | slack(CONTENT_TOO_LARGE, "")                                       || PAYLOAD_REFUSED
    "Slack answers 400 about its own team"           | slack(BAD_REQUEST, "no_team")                                      || NOT_TAKEN
    "Slack answers 400 about the webhook"            | slack(BAD_REQUEST, "no_service_id")                                || NOT_TAKEN
    "the channel is not found"                       | slack(NOT_FOUND, "channel_not_found")                              || NOT_TAKEN
    "Slack limits the rate"                          | slack(TOO_MANY_REQUESTS, "rate_limited")                           || NOT_TAKEN
    "Slack fails"                                    | server(INTERNAL_SERVER_ERROR)                                      || NOT_TAKEN
    "Slack is unavailable"                           | server(SERVICE_UNAVAILABLE)                                        || NOT_TAKEN
    "anything else goes wrong"                       | new IllegalStateException("No webhook for slack channel INVESTMENT") || NOT_TAKEN
  }

  private static ResourceAccessException io(IOException cause) {
    new ResourceAccessException("I/O error on POST request: " + cause.message, cause)
  }

  private static HttpClientErrorException slack(HttpStatus status, String body) {
    HttpClientErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, body.getBytes(UTF_8), UTF_8)
  }

  private static HttpServerErrorException server(HttpStatus status) {
    HttpServerErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, new byte[0], UTF_8)
  }
}
