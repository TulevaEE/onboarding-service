package ee.tuleva.onboarding.notification.slack

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sun.net.httpserver.HttpServer
import ee.tuleva.onboarding.config.http.RestTemplateConfiguration
import ee.tuleva.onboarding.error.RestResponseErrorHandler
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier
import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.HttpHostConnectException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration
import org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import spock.lang.Specification
import spock.lang.Timeout
import tools.jackson.databind.json.JsonMapper

import static java.nio.charset.StandardCharsets.ISO_8859_1
import static java.nio.charset.StandardCharsets.UTF_8
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING
import static ee.tuleva.onboarding.notification.slack.SlackService.SAME_SETTINGS_AS_SHARED_HTTP_CLIENTS
import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class SlackServiceSpec extends Specification {

  private static final String WEBHOOK_PATH = "/services/T000/B000/not-a-real-token"

  private static final String WEBHOOK_URL = "https://hooks.slack.com" + WEBHOOK_PATH

  private String dummyWebhookUrl = "https://example.com"

  SlackWebhookConfiguration webhookConfiguration = new SlackWebhookConfiguration()

  Environment environment = Mock()

  SlackService slackService

  MockRestServiceServer server

  def setup() {
    def restTemplate = SlackService.restTemplateKeepingTheWebhookUrlPrivate(SAME_SETTINGS_AS_SHARED_HTTP_CLIENTS)
    server = MockRestServiceServer.bindTo(restTemplate).build()
    slackService = new SlackService(restTemplate, webhookConfiguration, environment)
  }

  def "should send message to Slack if webhook URL is present"() {
    given:
    def testMessage = "Test Message 😎"

    webhookConfiguration.webhooks = [aml: dummyWebhookUrl]

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

    webhookConfiguration.webhooks = [investment: dummyWebhookUrl]

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

    webhookConfiguration.webhooks = [investment: dummyWebhookUrl]

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

    webhookConfiguration.webhooks = [trading: dummyWebhookUrl]

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
    webhookConfiguration.webhooks = [:]
    environment.matchesProfiles("production") >> false


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    server.verify()
  }


  def "throws if webhook URL is not present and in production"() {
    given:
    webhookConfiguration.webhooks = [:]
    environment.matchesProfiles("production") >> true


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    thrown(IllegalStateException)
    server.verify()
  }

  def "a failed post never carries the webhook URL, and keeps the type, status, headers and body, read in the charset Slack answered with: #status"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
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
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
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
    webhookConfiguration.webhooks = [trading: "http://localhost:${closedPort}${WEBHOOK_PATH}".toString()]
    def overRealHttpClient = new SlackService(webhookConfiguration, environment)

    when:
    overRealHttpClient.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause instanceof HttpHostConnectException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  @Timeout(10)
  def "a post Slack never answers fails on the read timeout with a socket timeout, and never carries the webhook path"() {
    given:
    def silentSlack = new ServerSocket(0, 50, InetAddress.loopbackAddress)
    def held = new CopyOnWriteArrayList<Socket>()
    Thread.startDaemon {
      while (!silentSlack.closed) {
        try {
          held << silentSlack.accept()
        } catch (IOException ignored) {
        }
      }
    }
    webhookConfiguration.webhooks = [trading: "http://127.0.0.1:${silentSlack.localPort}${WEBHOOK_PATH}".toString()]
    def overRealHttpClient = new SlackService(
        SlackService.restTemplateKeepingTheWebhookUrlPrivate(SAME_SETTINGS_AS_SHARED_HTTP_CLIENTS.withReadTimeout(Duration.ofMillis(300))),
        webhookConfiguration,
        environment)

    when:
    overRealHttpClient.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause instanceof SocketTimeoutException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    cleanup:
    silentSlack.close()
    held.each { it.close() }
  }

  def "a post failing in any other way never carries the webhook URL"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
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

  def "a Slack post waits as long as the shared HTTP clients configured in spring.http.clients do"() {
    given:
    def sharedHttpClients = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration))

    when:
    def sharedSettings = null
    sharedHttpClients.run { context -> sharedSettings = context.getBean(HttpClientSettings) }

    then:
    SAME_SETTINGS_AS_SHARED_HTTP_CLIENTS == sharedSettings
  }

  def "a Slack post sends its JSON in one piece with a Content-Length"() {
    given:
    def received = new CopyOnWriteArrayList<Map<String, String>>()
    def slack = localSlack(received)
    webhookConfiguration.webhooks = [trading: webhookOf(slack)]
    def overRealHttpClient = new SlackService(webhookConfiguration, environment)

    when:
    overRealHttpClient.sendMessage("Orders sent", TRADING)

    then:
    received == [[body: '{"text":"Orders sent"}', contentLength: "22", transferEncoding: null]]

    cleanup:
    slack.stop(0)
  }

  def "a Slack post is neither logged nor observed by the shared HTTP client setup, so no log line, metric or span carries the webhook URL"() {
    given:
    def received = new CopyOnWriteArrayList<Map<String, String>>()
    def slack = localSlack(received)
    def observations = new RecordingObservationHandler()
    def requestLogger = (Logger) LoggerFactory.getLogger(RestTemplateConfiguration)
    def previousLevel = requestLogger.level
    def requestLog = new ListAppender<ILoggingEvent>()
    requestLog.start()
    requestLogger.addAppender(requestLog)
    requestLogger.level = Level.INFO

    when:
    sharedHttpClientSetup(webhookOf(slack), observations).run { context ->
      context.getBean(SlackService).sendMessage("Orders sent", TRADING)
    }

    then:
    received*.body == ['{"text":"Orders sent"}']
    observations.started == []
    requestLog.list*.formattedMessage.findAll { it.contains(WEBHOOK_PATH) } == []

    cleanup:
    requestLogger.detachAppender(requestLog)
    requestLogger.level = previousLevel
    slack.stop(0)
  }

  private static ApplicationContextRunner sharedHttpClientSetup(String webhookUrl, RecordingObservationHandler observations) {
    def observationRegistry = ObservationRegistry.create()
    observationRegistry.observationConfig().observationHandler(observations)
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration, RestTemplateAutoConfiguration))
        .withUserConfiguration(RestTemplateConfiguration, SlackWebhookConfiguration)
        .withBean(RestResponseErrorHandler, { new RestResponseErrorHandler(JsonMapper.builder().build()) } as Supplier)
        .withBean(ObservationRestTemplateCustomizer, {
          new ObservationRestTemplateCustomizer(observationRegistry, new DefaultClientRequestObservationConvention())
        } as Supplier)
        .withBean(SlackService)
        .withPropertyValues("slack.webhooks.trading=" + webhookUrl)
  }

  private static HttpServer localSlack(List<Map<String, String>> received) {
    def slack = HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
    slack.createContext(WEBHOOK_PATH) { exchange ->
      received << [
          body            : new String(exchange.requestBody.readAllBytes(), UTF_8),
          contentLength   : exchange.requestHeaders.getFirst("Content-Length"),
          transferEncoding: exchange.requestHeaders.getFirst("Transfer-Encoding")
      ]
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    slack.start()
    slack
  }

  private static String webhookOf(HttpServer slack) {
    "http://127.0.0.1:${slack.address.port}${WEBHOOK_PATH}".toString()
  }

  private static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

    final List<String> started = new CopyOnWriteArrayList<>()

    @Override
    boolean supportsContext(Observation.Context context) {
      true
    }

    @Override
    void onStart(Observation.Context context) {
      started << context.name
    }
  }

  private static String printedWithEveryCause(Throwable failure) {
    def printed = new StringWriter()
    failure.printStackTrace(new PrintWriter(printed))
    printed.toString()
  }
}
