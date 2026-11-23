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
import org.apache.hc.client5.http.CircularRedirectException
import org.apache.hc.client5.http.ClientProtocolException
import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.HttpHostConnectException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration
import org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.UnknownContentTypeException
import org.springframework.web.client.UnknownHttpStatusCodeException
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
import static org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder.httpComponents
import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.http.HttpStatus.BAD_GATEWAY
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.OK
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class SlackServiceSpec extends Specification {

  private static final String WEBHOOK_PATH = "/services/T000/B000/not-a-real-token"

  private static final String WEBHOOK_URL = "https://hooks.slack.com" + WEBHOOK_PATH

  private static final String WEBHOOK_TOKEN = "not-a-real-token"

  private String dummyWebhookUrl = "https://example.com"

  SlackWebhookConfiguration webhookConfiguration = new SlackWebhookConfiguration()

  Environment environment = Mock()

  SlackService slackService

  MockRestServiceServer server

  def setup() {
    def restClient = SlackService.restClientOutsideTheSharedSetup(httpComponents(), HttpClientSettings.defaults())
    server = MockRestServiceServer.bindTo(restClient).build()
    slackService = new SlackService(restClient.build(), webhookConfiguration, environment)
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
    def reservedPort = new Socket()
    reservedPort.bind(new InetSocketAddress(InetAddress.loopbackAddress, 0))
    webhookConfiguration.webhooks = [trading: "http://127.0.0.1:${reservedPort.localPort}${WEBHOOK_PATH}".toString()]
    def overRealHttpClient = slackServiceOverRealHttpClient()

    when:
    overRealHttpClient.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause instanceof HttpHostConnectException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    cleanup:
    reservedPort.close()
  }

  def "an I/O error that names the webhook is replaced by one that names only its class, so the post never carries the webhook URL"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    def circularRedirect = "Circular redirect to '" + WEBHOOK_URL + "'"
    server.expect(requestTo(WEBHOOK_URL))
        .andRespond(withException(new ClientProtocolException(circularRedirect, new CircularRedirectException(circularRedirect))))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause.class == IOException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  def "a webhook that is not a valid URI is refused before any request, and the refusal never carries the webhook path: #invalidPart"() {
    given:
    def invalidPath = "/services/T000/B000/" + invalidPart
    webhookConfiguration.webhooks = [trading: "https://hooks.slack.com" + invalidPath]

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(IllegalArgumentException)
    !printedWithEveryCause(failure).contains(invalidPath)
    server.verify()

    where:
    invalidPart << ["not-a-real-{token", "not a real token"]
  }

  def "a webhook that is not an absolute http or https URL with a host is refused before any request, without carrying the webhook path: #webhookUrl"() {
    given:
    webhookConfiguration.webhooks = [trading: webhookUrl]

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(IllegalArgumentException)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
    server.verify()

    where:
    webhookUrl << [
        "https:" + WEBHOOK_PATH,
        "https://" + WEBHOOK_PATH,
        "ftp://hooks.slack.com" + WEBHOOK_PATH,
        "hooks.slack.com" + WEBHOOK_PATH
    ]
  }

  def "a webhook with surrounding whitespace, such as a trailing newline from a secret mount, still posts"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL + "\n"]
    server.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage("Orders sent", TRADING)

    then:
    server.verify()
  }

  def "an I/O error that names only the webhook's token is replaced as well, so the post never carries the token"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL))
        .andRespond(withException(new ClientProtocolException("Rejected credential " + WEBHOOK_TOKEN)))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause.class == IOException
    !printedWithEveryCause(failure).contains(WEBHOOK_TOKEN)
  }

  def "an I/O error that names the webhook's decoded path is replaced as well, so the post never carries it"() {
    given:
    def encodedUrl = "https://hooks.slack.com/services/T000/B000/not%20a%20real%20token"
    webhookConfiguration.webhooks = [trading: encodedUrl]
    server.expect(requestTo(encodedUrl))
        .andRespond(withException(new ClientProtocolException(
            "Circular redirect to 'https://hooks.slack.com/services/T000/B000/not a real token'")))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause.class == IOException
    !printedWithEveryCause(failure).contains("not a real token")
  }

  def "an answer that echoes the webhook keeps its type and status, and neither its body nor its headers carry the webhook URL"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL)).andRespond(withStatus(BAD_GATEWAY)
        .contentType(MediaType.TEXT_HTML)
        .location(URI.create(WEBHOOK_URL))
        .body("<html><body>Bad gateway for " + WEBHOOK_URL + "</body></html>"))

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(HttpServerErrorException.BadGateway)
    failure.statusCode == BAD_GATEWAY
    failure.responseHeaders.contentType == MediaType.TEXT_HTML
    !failure.responseBodyAsString.contains(WEBHOOK_PATH)
    !failure.responseHeaders.toString().contains(WEBHOOK_PATH)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  def "an answer in a content type the client cannot read keeps its type and status, and never carries the webhook URL"() {
    given:
    def headers = new HttpHeaders()
    headers.contentType = MediaType.TEXT_HTML
    headers.location = URI.create(WEBHOOK_URL)
    def unreadable = new UnknownContentTypeException(
        String, MediaType.TEXT_HTML, OK, "OK", headers, ("<html>" + WEBHOOK_URL + "</html>").getBytes(UTF_8))
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL)).andRespond({ request -> throw unreadable } as ResponseCreator)

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(UnknownContentTypeException)
    failure.statusCode == OK
    failure.contentType == MediaType.TEXT_HTML
    failure.targetType == String
    !failure.responseBodyAsString.contains(WEBHOOK_PATH)
    !failure.responseHeaders.toString().contains(WEBHOOK_PATH)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  def "a post that gets no answer keeps its type when its cause is not an I/O error, and never carries the webhook URL: #cause"() {
    given:
    def noAnswer = new ResourceAccessException("I/O error on POST request for \"" + WEBHOOK_URL + "\"")
    if (cause != null) {
      noAnswer.initCause(cause)
    }
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL)).andRespond({ request -> throw noAnswer } as ResponseCreator)

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(ResourceAccessException)
    failure.class == ResourceAccessException
    failure.cause.is(cause)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    where:
    cause << [null, new IllegalStateException("Connection pool shut down")]
  }

  def "a Slack post gets the connect and read timeouts configured under spring.http.clients in application.yml"() {
    given:
    def sharedSettings = null
    def configuredTimeouts = null
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration))
        .run { context ->
          sharedSettings = context.getBean(HttpClientSettings)
          def configuration = Binder.get(context.environment)
          configuredTimeouts = [
              configuration.bind("spring.http.clients.connect-timeout", Duration).get(),
              configuration.bind("spring.http.clients.read-timeout", Duration).get()
          ]
        }
    def builtWith = new CopyOnWriteArrayList<HttpClientSettings>()
    def recordingFactoryBuilder = { HttpClientSettings settings ->
      builtWith << settings
      httpComponents().build(settings)
    } as ClientHttpRequestFactoryBuilder

    when:
    new SlackService(webhookConfiguration, environment, recordingFactoryBuilder, sharedSettings)

    then:
    builtWith.collect { [it.connectTimeout(), it.readTimeout()] } == [configuredTimeouts]
  }

  def "a blank webhook counts as missing, so nothing is posted outside production: '#blank'"() {
    given:
    webhookConfiguration.webhooks = [aml: blank]
    environment.matchesProfiles("production") >> false

    when:
    slackService.sendMessage("Test Message", AML)

    then:
    noExceptionThrown()
    server.verify()

    where:
    blank << ["", "  "]
  }

  def "a blank webhook counts as missing, so in production the post fails for having no webhook: '#blank'"() {
    given:
    webhookConfiguration.webhooks = [aml: blank]
    environment.matchesProfiles("production") >> true

    when:
    slackService.sendMessage("Test Message", AML)

    then:
    thrown(IllegalStateException)
    server.verify()

    where:
    blank << ["", "  "]
  }

  def "any other answer Slack gives keeps its type, status, headers and body, and never carries the webhook URL: #answer.class.simpleName"() {
    given:
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL)).andRespond({ request -> throw answer } as ResponseCreator)

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(RestClientResponseException)
    failure.class == answer.class
    failure.statusCode == answer.statusCode
    failure.statusText == answer.statusText
    failure.responseHeaders.contentType == answer.responseHeaders.contentType
    failure.responseBodyAsString == answer.responseBodyAsString
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    where:
    answer << [
        new UnknownHttpStatusCodeException("599 Unknown on POST request for \"" + WEBHOOK_URL + "\"", 599, "Unknown", textHeaders(), "rollup".getBytes(UTF_8), UTF_8),
        new RestClientResponseException("418 on POST request for \"" + WEBHOOK_URL + "\"", 418, "I'm a teapot", textHeaders(), "short".getBytes(UTF_8), UTF_8)
    ]
  }

  def "a post failing in any other way keeps a cause that does not name the webhook, and never carries the webhook URL"() {
    given:
    def cause = new IllegalStateException("Response body could not be read")
    webhookConfiguration.webhooks = [trading: WEBHOOK_URL]
    server.expect(requestTo(WEBHOOK_URL)).andRespond({ request ->
      throw new RestClientException("Error while extracting response from ${request.URI}", cause)
    } as ResponseCreator)

    when:
    slackService.sendMessage("Orders NOT sent", TRADING, ERROR)

    then:
    def failure = thrown(RestClientException)
    failure.class == RestClientException
    failure.cause.is(cause)
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  @Timeout(10)
  def "a Slack post follows the shared spring.http.clients settings, so an overridden read timeout applies to Slack too"() {
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
    def application = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration, HttpClientAutoConfiguration, ImperativeHttpClientAutoConfiguration))
        .withUserConfiguration(SlackWebhookConfiguration)
        .withBean(SlackService)
        .withPropertyValues(
            "spring.http.clients.read-timeout=300ms",
            "slack.webhooks.trading=http://127.0.0.1:${silentSlack.localPort}${WEBHOOK_PATH}".toString())

    when:
    application.run { context -> context.getBean(SlackService).sendMessage("Orders NOT sent", TRADING, ERROR) }

    then:
    def failure = thrown(ResourceAccessException)
    failure.cause instanceof SocketTimeoutException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)

    cleanup:
    silentSlack.close()
    held.each { it.close() }
  }

  def "a post failing in any other way replaces a cause that names the webhook, and never carries the webhook URL"() {
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
    failure.cause.class == IOException
    !printedWithEveryCause(failure).contains(WEBHOOK_PATH)
  }

  def "a Slack post sends its JSON in one piece with a Content-Length"() {
    given:
    def received = new CopyOnWriteArrayList<Map<String, String>>()
    def slack = localSlack(received)
    webhookConfiguration.webhooks = [trading: webhookOf(slack)]
    def overRealHttpClient = slackServiceOverRealHttpClient()

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
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration,
            HttpClientAutoConfiguration,
            ImperativeHttpClientAutoConfiguration,
            RestTemplateAutoConfiguration))
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

  private SlackService slackServiceOverRealHttpClient() {
    new SlackService(webhookConfiguration, environment, ClientHttpRequestFactoryBuilder.detect(), HttpClientSettings.defaults())
  }

  private static HttpHeaders textHeaders() {
    def headers = new HttpHeaders()
    headers.contentType = MediaType.parseMediaType("text/plain;charset=UTF-8")
    headers
  }

  private static String printedWithEveryCause(Throwable failure) {
    def printed = new StringWriter()
    failure.printStackTrace(new PrintWriter(printed))
    printed.toString()
  }
}
