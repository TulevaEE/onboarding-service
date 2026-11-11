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

class AmlServiceKycCheckerConfigurationTest {

  private final AmlServiceKycCheckerConfiguration configuration =
      new AmlServiceKycCheckerConfiguration();

  @Test
  @Timeout(value = 30, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpWithinTheKycOnboardingReadTimeoutNotTheNightlyFeedsOne() throws IOException {
    try (var unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var properties =
          new AmlServiceProperties(
              "http://127.0.0.1:" + unresponsiveServer.getLocalPort(),
              "test-api-token-of-at-least-thirty-two-characters",
              ofSeconds(60),
              ofSeconds(60),
              ofSeconds(60),
              ofSeconds(60),
              ofMillis(500),
              ofMillis(250));
      var restClient =
          AmlServiceKycCheckerConfiguration.kycOnboardingRestClient(
              RestClient.builder(), properties);

      assertThatThrownBy(
              () ->
                  restClient
                      .post()
                      .uri("/v1/kyc-onboarding/assessments")
                      .retrieve()
                      .toBodilessEntity())
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

    assertThat(configuration.amlServiceKycChecker(RestClient.builder(), properties)).isNotNull();
  }
}
