package ee.tuleva.onboarding.notification.email;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

class EmailServiceMandrillTimeoutTest {

  private static final int BACKLOG_FOR_EVERY_RETRY_ATTEMPT = 10;
  private static final Duration SHORT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration SHORT_ANSWER_TIMEOUT = Duration.ofSeconds(1);
  private static final int WAIT_FOR_ANOTHER_CONNECTION_MILLIS = 300;
  private static final String HEADERS_PROMISING_MORE_BODY_THAN_SENT =
      "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n[";

  private final PersonImpl person = new PersonImpl("38888888888", "Test", "Person");
  private final List<Socket> heldConnections = new CopyOnWriteArrayList<>();
  private final AtomicInteger answeredConnections = new AtomicInteger();
  private final CountDownLatch headersAndPartOfTheBodySent = new CountDownLatch(1);

  private ServerSocket mandrill;
  private EmailService emailService;

  @BeforeEach
  void startMandrillThatStalls() throws IOException, URISyntaxException {
    mandrill =
        new ServerSocket(0, BACKLOG_FOR_EVERY_RETRY_ATTEMPT, InetAddress.getLoopbackAddress());
    var shortTimeouts =
        MandrillHttp.restClient(mandrillUri(), SHORT_CONNECT_TIMEOUT, SHORT_ANSWER_TIMEOUT);
    var mandrillClient = new MandrillClient("test-mandrill-key", shortTimeouts, shortTimeouts);
    emailService =
        new EmailService(mandrillClient, MandrillConfiguration.emailServiceRetryTemplate());
  }

  @AfterEach
  void stopMandrill() throws IOException {
    mandrill.close();
    for (Socket connection : heldConnections) {
      connection.close();
    }
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void sendGivesUpPromptlyWhenMandrillTakesTheConnectionButNeverAnswers() throws IOException {
    var result = emailService.send(person, message(), "template");

    assertThat(result).isEmpty();
    assertThat(connectionsWaitingToBeAccepted()).isEqualTo(1);
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void sendSystemEmailGivesUpWithoutSendingAgainWhenMandrillTakesTheConnectionButNeverAnswers()
      throws IOException {
    var sent = emailService.sendSystemEmail(message());

    assertThat(sent).isFalse();
    assertThat(connectionsWaitingToBeAccepted()).isEqualTo(1);
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void sendGivesUpPromptlyWhenMandrillStallsHalfwayThroughItsAnswer() {
    answerWithHeadersAndThenStall();

    var result = emailService.send(person, message(), "template");

    assertThat(result).isEmpty();
    assertThat(headersAndPartOfTheBodySent.getCount()).isZero();
    assertThat(answeredConnections.get()).isEqualTo(1);
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void sendSystemEmailGivesUpWithoutSendingAgainWhenMandrillStallsHalfwayThroughItsAnswer() {
    answerWithHeadersAndThenStall();

    var sent = emailService.sendSystemEmail(message());

    assertThat(sent).isFalse();
    assertThat(headersAndPartOfTheBodySent.getCount()).isZero();
    assertThat(answeredConnections.get()).isEqualTo(1);
  }

  private MandrillMessage message() {
    return emailService.newMandrillMessage("someone@example.com", "template", Map.of(), List.of());
  }

  private URI mandrillUri() throws URISyntaxException {
    return new URI(
        "http",
        null,
        mandrill.getInetAddress().getHostAddress(),
        mandrill.getLocalPort(),
        "/api/1.0",
        null,
        null);
  }

  private int connectionsWaitingToBeAccepted() throws IOException {
    mandrill.setSoTimeout(WAIT_FOR_ANOTHER_CONNECTION_MILLIS);
    var connections = 0;
    while (true) {
      try {
        heldConnections.add(mandrill.accept());
        connections++;
      } catch (SocketTimeoutException noMoreConnections) {
        return connections;
      }
    }
  }

  private void answerWithHeadersAndThenStall() {
    Thread.ofVirtual()
        .start(
            () -> {
              while (!mandrill.isClosed()) {
                try {
                  Socket connection = mandrill.accept();
                  heldConnections.add(connection);
                  answeredConnections.incrementAndGet();
                  readRequest(connection.getInputStream());
                  connection
                      .getOutputStream()
                      .write(HEADERS_PROMISING_MORE_BODY_THAN_SENT.getBytes(US_ASCII));
                  connection.getOutputStream().flush();
                  headersAndPartOfTheBodySent.countDown();
                } catch (IOException closed) {
                  return;
                }
              }
            });
  }

  private static void readRequest(InputStream request) throws IOException {
    var headers = new ByteArrayOutputStream();
    while (!headers.toString(US_ASCII).endsWith("\r\n\r\n")) {
      int next = request.read();
      if (next < 0) {
        return;
      }
      headers.write(next);
    }
    request.readNBytes(contentLength(headers.toString(US_ASCII)));
  }

  private static int contentLength(String headers) {
    return headers
        .lines()
        .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("content-length:"))
        .map(line -> Integer.parseInt(line.substring("content-length:".length()).trim()))
        .findFirst()
        .orElse(0);
  }
}
