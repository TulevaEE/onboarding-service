package ee.tuleva.onboarding.notification.slack

import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.HttpHostConnectException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import spock.lang.Specification

import static java.nio.charset.StandardCharsets.ISO_8859_1
import static java.nio.charset.StandardCharsets.UTF_8
import static org.mockito.Mockito.when
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel
import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(SlackService)
class SlackServiceSpec extends Specification {

  @Autowired
  SlackService slackService

  @Autowired
  MockRestServiceServer server

  @MockitoBean
  SlackWebhookConfiguration webhookConfiguration

  Environment environment = Mock()

  private String dummyWebhookUrl = "https://example.com"

  private static final String WEBHOOK_PATH = "/services/T000/B000/not-a-real-token"

  private static final String WEBHOOK_URL = "https://hooks.slack.com" + WEBHOOK_PATH

  def setup() {
    slackService.environment = environment
  }

  def cleanup() {
    server.reset()
  }

  def "should send message to Slack if webhook URL is present"() {
    given:
    def testMessage = "Test Message 😎"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.text').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, AML)

    then:
    server.verify()
  }

  def "should send error message as a red attachment"() {
    given:
    def testMessage = "NAV publish still missing"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.INVESTMENT)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.attachments[0].color').value("danger"))
        .andExpect(jsonPath('$.attachments[0].text').value(testMessage))
        .andExpect(jsonPath('$.attachments[0].fallback').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, INVESTMENT, ERROR)

    then:
    server.verify()
  }

  def "should send warning message as a yellow attachment"() {
    given:
    def testMessage = "Ownership share over the soft limit"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.INVESTMENT)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.attachments[0].color').value("warning"))
        .andExpect(jsonPath('$.attachments[0].text').value(testMessage))
        .andExpect(jsonPath('$.attachments[0].fallback').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, INVESTMENT, WARNING)

    then:
    server.verify()
  }

  def "sends a trading message to the trading webhook"() {
    given:
    def testMessage = "Orders emailed"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.TRADING)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(jsonPath('$.text').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, TRADING)

    then:
    server.verify()
  }

  def "should not send message to Slack if webhook URL is not present and not in production"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(null)
    environment.matchesProfiles("production") >> false


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    server.verify()
  }


  def "throws if webhook URL is not present and in production"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(null)
    environment.matchesProfiles("production") >> true


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    thrown(IllegalStateException)
    server.verify()
  }

  def "a failed post never carries the webhook URL, and keeps the type, status, headers and body, read in the charset Slack answered with: #status"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.TRADING)).thenReturn(WEBHOOK_URL)
    server.expect(requestTo(WEBHOOK_URL))
        .andRespond(withStatus(status).contentType(new MediaType(TEXT_PLAIN, charset)).body(body.getBytes(charset)))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(RestClientResponseException)
    failure.class == expected
    failure.statusCode == status
    failure.statusText == status.reasonPhrase
    failure.responseHeaders.contentType == new MediaType(TEXT_PLAIN, charset)
    failure.responseBodyAsString == body
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    where:
    status                | body                       | charset    | expected
    BAD_REQUEST           | "invalid_payload"          | UTF_8      | HttpClientErrorException.BadRequest
    NOT_FOUND             | "no_service"               | UTF_8      | HttpClientErrorException.NotFound
    INTERNAL_SERVER_ERROR | "rollup_error · try again" | ISO_8859_1 | HttpServerErrorException.InternalServerError
  }

  def "a post that gets no answer never carries the webhook URL, and keeps the I/O error that caused it: #cause"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.TRADING)).thenReturn(WEBHOOK_URL)
    server.expect(requestTo(WEBHOOK_URL)).andRespond(withException(cause))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause.is(cause)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    where:
    cause << [
        new SocketTimeoutException("Read timed out"),
        new ConnectTimeoutException("Connect timed out")
    ]
  }

  def "a post to a port nobody listens on never carries the webhook path, since HttpClient5 names only the host and port"() {
    given:
    def closedPort = new ServerSocket(0).withCloseable { it.localPort }
    def webhookUrl = "http://localhost:${closedPort}${WEBHOOK_PATH}".toString()
    when(webhookConfiguration.getWebhookUrl(SlackChannel.TRADING)).thenReturn(webhookUrl)
    def overRealHttpClient = new SlackService(new RestTemplateBuilder(), webhookConfiguration, environment)

    when:
    overRealHttpClient.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause instanceof HttpHostConnectException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  def "a post failing in any other way never carries the webhook URL"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.TRADING)).thenReturn(WEBHOOK_URL)
    server.expect(requestTo(WEBHOOK_URL)).andRespond({ request ->
      throw new RestClientException("Could not post to ${request.URI}", new IOException(request.URI.toString()))
    } as ResponseCreator)

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(RestClientException)
    failure.class == RestClientException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  private static String printedWithEveryCause(Throwable failure) {
    def printed = new StringWriter()
    failure.printStackTrace(new PrintWriter(printed))
    printed.toString()
  }
}
