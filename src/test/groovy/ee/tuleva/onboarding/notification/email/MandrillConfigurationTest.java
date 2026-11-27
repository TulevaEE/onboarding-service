package ee.tuleva.onboarding.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class MandrillConfigurationTest {

  private static final String MANDRILL_URL = "https://mandrillapp.com/api/1.0";

  private final MandrillConfiguration configuration = new MandrillConfiguration();

  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(MandrillConfiguration.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logAppender);
  }

  @Test
  void mandrillClientIsPresentWhenTheKeyIsConfigured() {
    MandrillClient mandrillClient = configuration.mandrillClient(MANDRILL_URL, "test-mandrill-key");

    assertThat(mandrillClient).isNotNull();
    assertThat(logAppender.list).isEmpty();
  }

  @Test
  void mandrillClientIsAbsentWithAWarningWhenTheKeyIsMissing() {
    MandrillClient mandrillClient = configuration.mandrillClient(MANDRILL_URL, null);

    assertThat(mandrillClient).isNull();
    assertThat(logAppender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
  }

  @Test
  void emailServiceRetryTemplateRetriesWhenAnEarlierAttemptNeverReachedMandrill()
      throws RetryException {
    RetryTemplate retryTemplate = MandrillConfiguration.emailServiceRetryTemplate();
    var attempts = new AtomicInteger();

    String result =
        retryTemplate.execute(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw neverReachedMandrill();
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failuresAndAttempts")
  void emailServiceRetryTemplateSendsAgainBeforeAnAnswerAndOnRefusalsButNotOnceAnAnswerBegan(
      RuntimeException thrown, int expectedAttempts) {
    RetryTemplate retryTemplate =
        MandrillConfiguration.emailServiceRetryTemplateWithFirstDelay(Duration.ofMillis(1));
    var attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw thrown;
                    }))
        .isInstanceOf(RetryException.class);
    assertThat(attempts.get()).isEqualTo(expectedAttempts);
  }

  static Stream<Arguments> failuresAndAttempts() {
    return Stream.of(
        arguments(named("the connection was refused", neverReachedMandrill()), 3),
        arguments(
            named(
                "the connection timed out",
                new ResourceAccessException(
                    "I/O error", new HttpConnectTimeoutException("HTTP connect timed out"))),
            3),
        arguments(
            named(
                "the connection was reset before any answer",
                new ResourceAccessException("I/O error", new IOException("Connection reset"))),
            3),
        arguments(
            named(
                "Mandrill or its edge answered with an error",
                new MandrillApiException(
                    "/messages/send.json", new MandrillError(502, null, null, null))),
            3),
        arguments(
            named(
                "Mandrill did not answer in time",
                new ResourceAccessException(
                    "I/O error", new HttpTimeoutException("request timed out"))),
            1),
        arguments(
            named(
                "the answer stalled or broke off after it began",
                new RestClientException(
                    "Error while extracting response", new IOException("closed"))),
            1),
        arguments(
            named("the answer could not be read", new JsonSyntaxException("Expected BEGIN_ARRAY")),
            1));
  }

  @Test
  void emailServiceRetryTemplateDoesNotRetryUnlistedExceptions() {
    RetryTemplate retryTemplate = MandrillConfiguration.emailServiceRetryTemplate();
    var attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.invoke(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("not retryable");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }

  private static ResourceAccessException neverReachedMandrill() {
    return new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
  }
}
