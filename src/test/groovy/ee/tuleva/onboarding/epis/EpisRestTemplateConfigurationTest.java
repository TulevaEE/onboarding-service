package ee.tuleva.onboarding.epis;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

class EpisRestTemplateConfigurationTest {

  private final EpisRestTemplateConfiguration configuration = new EpisRestTemplateConfiguration();
  private final RestResponseErrorHandler errorHandler =
      new RestResponseErrorHandler(JsonMapper.builder().build());

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void longRequestTemplateAppliesTheConfiguredReadTimeout() throws IOException {
    RestTemplate restTemplate =
        configuration.episLongRequestRestTemplate(
            new RestTemplateBuilder(), errorHandler, Duration.ofMillis(250));

    try (var unresponsiveServer = unresponsiveServer()) {
      Throwable thrown =
          catchThrowable(() -> restTemplate.getForObject(url(unresponsiveServer), String.class));

      assertThat(thrown).isInstanceOf(ResourceAccessException.class);
    }
  }

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void standardTemplateAppliesTheConfiguredReadTimeout() throws IOException {
    RestTemplate restTemplate =
        configuration.episRestTemplate(
            new RestTemplateBuilder(), errorHandler, Duration.ofMillis(250));

    try (var unresponsiveServer = unresponsiveServer()) {
      Throwable thrown =
          catchThrowable(() -> restTemplate.getForObject(url(unresponsiveServer), String.class));

      assertThat(thrown).isInstanceOf(ResourceAccessException.class);
    }
  }

  private ServerSocket unresponsiveServer() throws IOException {
    return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
  }

  private String url(ServerSocket server) {
    return "http://127.0.0.1:" + server.getLocalPort() + "/";
  }
}
