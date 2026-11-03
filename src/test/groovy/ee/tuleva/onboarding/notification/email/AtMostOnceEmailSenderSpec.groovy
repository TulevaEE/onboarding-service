package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN
import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.ExpectedCount.once
import static org.springframework.test.web.client.ExpectedCount.times
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class AtMostOnceEmailSenderSpec extends Specification {

  static final String SEND_URL = "https://mandrill.example.com/api/1.0/messages/send.json"

  RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://mandrill.example.com/api/1.0")
  MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build()
  AtMostOnceEmailSender sender = new AtMostOnceEmailSender("order-key", restClientBuilder.build(), Duration.ofMillis(1))

  MandrillMessage message = new MandrillMessage().tap {
    subject = "Orders"
    to = [
        recipient("desk@example.com", TO),
        recipient("desk-backup@example.com", TO),
        recipient("copy@example.com", CC)]
  }

  def "posts the message once with the order key and counts it as sent when every main recipient accepted it"() {
    given:
    server.expect(once(), requestTo(SEND_URL))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.key').value("order-key"))
        .andExpect(jsonPath('$.async').value(false))
        .andExpect(jsonPath('$.message.subject').value("Orders"))
        .andExpect(jsonPath('$.message.to[0].email').value("desk@example.com"))
        .andExpect(jsonPath('$.message.to[2].type').value("cc"))
        .andRespond(answer(
            status("desk@example.com", "sent"),
            status("desk-backup@example.com", "queued"),
            status("copy@example.com", "sent")))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == SENT
    result.statuses()*.email == ["desk@example.com", "desk-backup@example.com", "copy@example.com"]
    result.problem() == null
  }

  def "counts the email as sent when Mandrill rejects only a copy recipient"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(answer(
        status("desk@example.com", "sent"),
        status("desk-backup@example.com", "sent"),
        status("copy@example.com", "rejected")))

    when:
    def result = sender.send(message)

    then:
    result.outcome() == SENT
    result.notAccepted()*.email == ["copy@example.com"]
  }

  def "counts the email as failed when Mandrill rejects it for every main recipient"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(answer(
        status("desk@example.com", "rejected"),
        status("desk-backup@example.com", "invalid"),
        status("copy@example.com", "sent")))

    when:
    def result = sender.send(message)

    then:
    result.outcome() == FAILED
    result.problem() != null
  }

  def "counts the email as unknown when Mandrill accepts it for only some main recipients"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(answer(
        status("desk@example.com", "sent"),
        status("desk-backup@example.com", "rejected"),
        status("copy@example.com", "sent")))

    when:
    def result = sender.send(message)

    then:
    result.outcome() == UNKNOWN
  }

  def "counts the email as unknown when a main recipient has no status in the answer"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(answer(
        status("desk@example.com", "rejected"),
        status("copy@example.com", "sent")))

    when:
    def result = sender.send(message)

    then:
    result.outcome() == UNKNOWN
  }

  def "counts the email as unknown when Mandrill's answer cannot be read: #body"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

    when:
    def result = sender.send(message)

    then:
    result.outcome() == UNKNOWN
    result.statuses() == []
    result.problem() != null

    where:
    body << ["[]", "null", "[null]", "{\"unexpected\":true}", "not json"]
  }

  def "counts the email as failed and does not send again when Mandrill refuses the request before sending: #errorName"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(
        withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mandrillError(errorName)))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == FAILED
    result.problem() != null

    where:
    errorName << ["Invalid_Key", "ValidationError", "PaymentRequired", "Unknown_Subaccount"]
  }

  def "counts the email as unknown and does not send again when Mandrill answers with another error: #status"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(
        withStatus(status).contentType(MediaType.TEXT_HTML).body(body))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == UNKNOWN

    where:
    status                           | body
    HttpStatus.INTERNAL_SERVER_ERROR | mandrillError("GeneralError")
    HttpStatus.BAD_GATEWAY           | "<html>Bad gateway</html>"
    HttpStatus.SERVICE_UNAVAILABLE   | ""
  }

  def "does not send again when the request may have reached Mandrill, and counts the email as unknown: #failure"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(withException(failure))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == UNKNOWN
    result.statuses() == []

    where:
    failure << [
        new SocketTimeoutException("Read timed out"),
        new HttpTimeoutException("request timed out"),
        new IOException("Connection reset")]
  }

  def "tries again when the request did not reach Mandrill, and counts the email as sent when a later try is accepted"() {
    given:
    server.expect(once(), requestTo(SEND_URL)).andRespond(withException(new ConnectException("Connection refused")))
    server.expect(once(), requestTo(SEND_URL)).andRespond(answer(
        status("desk@example.com", "sent"),
        status("desk-backup@example.com", "sent")))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == SENT
  }

  def "counts the email as failed when no try reached Mandrill: #failure"() {
    given:
    server.expect(times(3), requestTo(SEND_URL)).andRespond(withException(failure))

    when:
    def result = sender.send(message)

    then:
    server.verify()
    result.outcome() == FAILED
    result.problem() != null

    where:
    failure << [
        new ConnectException("Connection refused"),
        new UnknownHostException("mandrill.example.com"),
        new HttpConnectTimeoutException("HTTP connect timed out")]
  }

  def "counts the email as failed without calling Mandrill when the sender has no key"() {
    when:
    def result = AtMostOnceEmailSender.withoutKey().send(message)

    then:
    server.verify()
    result.outcome() == FAILED
    result.problem() != null
  }

  def "counts the email as failed when Mandrill's port refuses the connection"() {
    given:
    def closedPort = new ServerSocket(0).withCloseable { it.localPort }
    def realSender = new AtMostOnceEmailSender(
        "order-key",
        AtMostOnceEmailSender.restClient(
            URI.create("http://127.0.0.1:$closedPort/api/1.0"), Duration.ofSeconds(2), Duration.ofSeconds(2)),
        Duration.ofMillis(1))

    when:
    def result = realSender.send(message)

    then:
    result.outcome() == FAILED
  }

  def "counts the email as unknown and does not send again when Mandrill does not answer in time"() {
    given:
    def connections = new AtomicInteger()
    def silentServer = new ServerSocket(0, 50, InetAddress.loopbackAddress)
    def held = new CopyOnWriteArrayList<Socket>()
    Thread.startDaemon {
      while (!silentServer.closed) {
        try {
          held << silentServer.accept()
          connections.incrementAndGet()
        } catch (IOException ignored) {
        }
      }
    }
    def realSender = new AtMostOnceEmailSender(
        "order-key",
        AtMostOnceEmailSender.restClient(
            URI.create("http://127.0.0.1:$silentServer.localPort/api/1.0"), Duration.ofSeconds(2), Duration.ofMillis(300)),
        Duration.ofMillis(1))

    when:
    def result = realSender.send(message)

    then:
    result.outcome() == UNKNOWN
    connections.get() == 1

    cleanup:
    silentServer.close()
    held.each { it.close() }
  }

  private static def answer(String... statuses) {
    withSuccess("[" + statuses.join(",") + "]", MediaType.APPLICATION_JSON)
  }

  private static String status(String email, String status) {
    """{"email":"$email","status":"$status","_id":"id-$email","reject_reason":null}"""
  }

  private static String mandrillError(String name) {
    """{"status":"error","code":-1,"name":"$name","message":"Mandrill error message"}"""
  }

  private static Recipient recipient(String email, Recipient.Type type) {
    new Recipient().tap {
      it.email = email
      it.type = type
    }
  }
}
