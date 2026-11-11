package ee.tuleva.onboarding.aml.risklevel;

import static ch.qos.logback.classic.Level.WARN;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AmlServiceConfigurationTest {

  private final AmlServiceConfiguration configuration = new AmlServiceConfiguration();
  private final Logger logger = (Logger) LoggerFactory.getLogger(AmlServiceConfiguration.class);
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

  @BeforeEach
  void attachLogAppender() {
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logAppender);
  }

  @Test
  @Timeout(value = 30, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpOnAnAmlServiceThatDoesNotAnswerWithinTheReadTimeout() throws IOException {
    try (var unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var properties =
          new AmlServiceProperties(
              "http://127.0.0.1:" + unresponsiveServer.getLocalPort(),
              "test-api-token-of-at-least-thirty-two-characters",
              ofMillis(500),
              ofMillis(250),
              ofMillis(500),
              ofSeconds(60),
              ofSeconds(1),
              ofSeconds(5));
      var restClient =
          AmlServiceConfiguration.amlServiceRestClient(RestClient.builder(), properties);

      assertThatThrownBy(
              () -> restClient.get().uri("/v1/risk-levels/tkf").retrieve().toBodilessEntity())
          .isInstanceOf(ResourceAccessException.class);
    }
  }

  @Test
  void startsWithoutAnApiToken() {
    var properties =
        new AmlServiceProperties(
            "https://aml-service.test",
            "",
            ofSeconds(5),
            ofSeconds(60),
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(5));

    assertThat(configuration.amlServiceClient(RestClient.builder(), properties)).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"production", "test", "ci,test", "pg,test"})
  void warnsOnceOfWhatAMissingApiTokenStopsInEveryProfileThatHasAnAmlService(String profiles) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profiles)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AmlServiceClient.class);
              assertThat(logAppender.list)
                  .extracting(ILoggingEvent::getLevel)
                  .containsExactly(WARN);
            });
  }

  @Test
  void warnsOfNothingWithAnApiToken() {
    contextRunner()
        .withPropertyValues(
            "aml-service.api-token=test-api-token-of-at-least-thirty-two-characters")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AmlServiceClient.class);
              assertThat(logAppender.list).isEmpty();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging"})
  void warnsOfNothingWithoutAnApiTokenInDevAndStagingWhichHaveNoAmlService(String profile) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(AmlServiceClient.class);
              assertThat(logAppender.list).isEmpty();
            });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(AmlServiceConfiguration.class)
        .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
        .withPropertyValues(
            "aml-service.url=https://aml-service.test",
            "aml-service.connect-timeout=5s",
            "aml-service.read-timeout=60s",
            "aml-service.high-risk-connect-timeout=1s",
            "aml-service.high-risk-read-timeout=2s",
            "aml-service.kyc-onboarding-connect-timeout=1s",
            "aml-service.kyc-onboarding-read-timeout=3s");
  }
}
