package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AmlServiceConfigurationTest {

  private final AmlServiceConfiguration configuration = new AmlServiceConfiguration();

  @Test
  @Timeout(value = 30, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpOnAnAmlServiceThatDoesNotAnswerWithinTheReadTimeout() throws IOException {
    try (var unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var properties =
          new AmlServiceProperties(
              true,
              "http://127.0.0.1:" + unresponsiveServer.getLocalPort(),
              "test-api-token-of-at-least-thirty-two-characters",
              ofMillis(500),
              ofMillis(250));
      var restClient =
          AmlServiceConfiguration.amlServiceRestClient(RestClient.builder(), properties);

      assertThatThrownBy(
              () -> restClient.get().uri("/v1/risk-levels/tkf").retrieve().toBodilessEntity())
          .isInstanceOf(ResourceAccessException.class);
    }
  }

  @Test
  void startsEnabledWithoutAnApiToken() {
    var properties =
        new AmlServiceProperties(true, "https://aml-service.test", "", ofSeconds(5), ofSeconds(60));

    assertThat(configuration.amlServiceClient(RestClient.builder(), properties)).isNotNull();
  }

  @Test
  void startsDisabledWithoutAnApiToken() {
    var properties =
        new AmlServiceProperties(
            false, "https://aml-service.test", "", ofSeconds(5), ofSeconds(60));

    assertThat(configuration.amlServiceClient(RestClient.builder(), properties)).isNotNull();
  }
}
