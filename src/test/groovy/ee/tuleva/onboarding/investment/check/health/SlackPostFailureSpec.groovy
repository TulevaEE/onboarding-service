package ee.tuleva.onboarding.investment.check.health

import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.HttpHostConnectException
import org.apache.hc.core5.http.NoHttpResponseException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification
import spock.lang.Unroll

import javax.net.ssl.SSLHandshakeException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.NOT_SENT
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.PAYLOAD_REFUSED
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.UNCERTAIN
import static java.nio.charset.StandardCharsets.UTF_8
import static org.springframework.http.HttpStatus.BAD_GATEWAY
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE
import static org.springframework.http.HttpStatus.FORBIDDEN
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS

class SlackPostFailureSpec extends Specification {

  static final String WEBHOOK = "https://hooks.slack.com/services/example-webhook-path"

  @Unroll
  def "aFailedPostIs #expected when #what"() {
    expect:
    SlackPostFailure.of(failure) == expected

    where:
    what                                          | failure                                                        || expected
    "the connection was refused"                  | io(new HttpHostConnectException("Connection refused"))         || NOT_SENT
    "a plain connection was refused"              | io(new ConnectException("Connection refused"))                 || NOT_SENT
    "the connection timed out"                    | io(new ConnectTimeoutException("Connect timed out"))           || NOT_SENT
    "the JDK client's connection timed out"       | io(new HttpConnectTimeoutException("HTTP connect timed out"))  || NOT_SENT
    "the host is unknown"                         | io(new UnknownHostException("hooks.slack.com"))                || NOT_SENT
    "there is no route to the host"               | io(new NoRouteToHostException("No route to host"))             || NOT_SENT
    "Slack limits the rate"                       | slack(TOO_MANY_REQUESTS, "rate_limited")                       || NOT_SENT
    "Slack names an invalid payload"              | slack(BAD_REQUEST, "invalid_payload")                          || PAYLOAD_REFUSED
    "Slack says the post has no text"             | slack(BAD_REQUEST, "no_text")                                  || PAYLOAD_REFUSED
    "Slack says it has too many attachments"      | slack(BAD_REQUEST, "too_many_attachments")                     || PAYLOAD_REFUSED
    "Slack says the payload is too large"         | slack(CONTENT_TOO_LARGE, "")                                   || PAYLOAD_REFUSED
    "the answer or the TLS handshake timed out"   | io(new SocketTimeoutException("Read timed out"))               || UNCERTAIN
    "the TLS handshake failed"                    | io(new SSLHandshakeException("Remote host terminated"))        || UNCERTAIN
    "the JDK client's request timed out"          | io(new HttpTimeoutException("request timed out"))              || UNCERTAIN
    "the connection was reset"                    | io(new SocketException("Connection reset"))                    || UNCERTAIN
    "Slack gave no response"                      | io(new NoHttpResponseException("failed to respond"))           || UNCERTAIN
    "Slack answers 400 about its own team"        | slack(BAD_REQUEST, "no_team")                                  || UNCERTAIN
    "Slack answers 400 about the webhook"         | slack(BAD_REQUEST, "no_service_id")                            || UNCERTAIN
    "Slack forbids the post"                      | slack(FORBIDDEN, "action_prohibited")                          || UNCERTAIN
    "the channel is not found"                    | slack(NOT_FOUND, "channel_not_found")                          || UNCERTAIN
    "Slack fails"                                 | server(INTERNAL_SERVER_ERROR)                                  || UNCERTAIN
    "a gateway in front of Slack fails"           | server(BAD_GATEWAY)                                            || UNCERTAIN
    "the webhook is not configured"               | new IllegalStateException("No webhook for slack channel")      || UNCERTAIN
  }

  def "aFailureIsDescribedByItsMostSpecificCauseWithoutTheWebhookUrl"() {
    given:
    def timedOut = new ResourceAccessException(
        "I/O error on POST request for \"" + WEBHOOK + "\": Read timed out",
        new SocketTimeoutException("Read timed out"))
    def refused = HttpClientErrorException.create(
        "400 Bad Request on POST request for \"" + WEBHOOK + "\": \"invalid_payload\"",
        BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "invalid_payload".getBytes(UTF_8), UTF_8)

    expect:
    SlackPostFailure.describe(timedOut) == "java.net.SocketTimeoutException: Read timed out"
    SlackPostFailure.describe(refused) ==
        'org.springframework.web.client.HttpClientErrorException$BadRequest:' +
        ' 400 Bad Request on POST request for "<url> "invalid_payload"'
  }

  private static ResourceAccessException io(IOException cause) {
    new ResourceAccessException("I/O error on POST request for \"" + WEBHOOK + "\": " + cause.message, cause)
  }

  private static HttpClientErrorException slack(HttpStatus status, String body) {
    HttpClientErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, body.getBytes(UTF_8), UTF_8)
  }

  private static HttpServerErrorException server(HttpStatus status) {
    HttpServerErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, new byte[0], UTF_8)
  }
}
