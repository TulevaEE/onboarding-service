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

class EffectiveRiskLevelConfigurationTest {

  private final EffectiveRiskLevelConfiguration configuration =
      new EffectiveRiskLevelConfiguration();

  @Test
  @Timeout(value = 30, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpWithinTheHighRiskReadTimeoutNotTheNightlyFeedsOne() throws IOException {
    try (var unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var properties =
          new AmlServiceProperties(
              true,
              "http://127.0.0.1:" + unresponsiveServer.getLocalPort(),
              "test-api-token-of-at-least-thirty-two-characters",
              ofSeconds(60),
              ofSeconds(60),
              true,
              ofMillis(500),
              ofMillis(250),
              ofSeconds(1),
              ofSeconds(5));
      var restClient = EffectiveRiskLevelConfiguration.effectiveRiskLevelRestClient(properties);

      assertThatThrownBy(
              () ->
                  restClient
                      .get()
                      .uri("/v1/risk-levels/effective/38888888888")
                      .retrieve()
                      .toBodilessEntity())
          .isInstanceOf(ResourceAccessException.class);
    }
  }

  @Test
  void startsWithHighRiskEnabledWithoutAnApiToken() {
    var properties =
        new AmlServiceProperties(
            false,
            "https://aml-service.test",
            "",
            ofSeconds(5),
            ofSeconds(60),
            true,
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(5));

    assertThat(configuration.effectiveRiskLevelClient(properties)).isNotNull();
  }
}
