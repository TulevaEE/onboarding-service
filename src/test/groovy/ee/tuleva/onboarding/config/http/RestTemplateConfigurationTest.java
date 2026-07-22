package ee.tuleva.onboarding.config.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class RestTemplateConfigurationTest {

  private final RestTemplateCustomizer loggingCustomizer =
      new RestTemplateConfiguration().loggingRestTemplateCustomizer();

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void configuredTimeoutsSurviveTheLoggingCustomizer() throws IOException {
    RestTemplate restTemplate =
        new RestTemplateBuilder()
            .customizers(loggingCustomizer)
            .connectTimeout(Duration.ofMillis(500))
            .readTimeout(Duration.ofMillis(250))
            .build();

    try (var unresponsiveServer = unresponsiveServer()) {
      Throwable thrown =
          catchThrowable(() -> restTemplate.getForObject(url(unresponsiveServer), String.class));

      assertThat(thrown).isInstanceOf(ResourceAccessException.class);
    }
  }

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void defaultTimeoutPropertiesApplyToClientsThatConfigureNone() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HttpClientAutoConfiguration.class,
                ImperativeHttpClientAutoConfiguration.class,
                RestTemplateAutoConfiguration.class))
        .withBean(RestTemplateCustomizer.class, () -> loggingCustomizer)
        .withPropertyValues(
            "spring.http.clients.connect-timeout=500ms", "spring.http.clients.read-timeout=250ms")
        .run(
            context -> {
              RestTemplate restTemplate = context.getBean(RestTemplateBuilder.class).build();

              try (var unresponsiveServer = unresponsiveServer()) {
                Throwable thrown =
                    catchThrowable(
                        () -> restTemplate.getForObject(url(unresponsiveServer), String.class));

                assertThat(thrown).isInstanceOf(ResourceAccessException.class);
              }
            });
  }

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void responseBodyRemainsReadableAfterLoggingInterceptor() throws IOException {
    RestTemplate restTemplate =
        new RestTemplateBuilder()
            .customizers(loggingCustomizer)
            .readTimeout(Duration.ofSeconds(2))
            .build();

    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      respondOnceWith(
          server, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok");

      String body = restTemplate.getForObject(url(server), String.class);

      assertThat(body).isEqualTo("ok");
    }
  }

  private ServerSocket unresponsiveServer() throws IOException {
    return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
  }

  private String url(ServerSocket server) {
    return "http://127.0.0.1:" + server.getLocalPort() + "/";
  }

  private void respondOnceWith(ServerSocket server, String response) {
    var responder =
        new Thread(
            () -> {
              try (var socket = server.accept();
                  var in = socket.getInputStream();
                  var out = socket.getOutputStream()) {
                in.read(new byte[4096]);
                out.write(response.getBytes(UTF_8));
                out.flush();
              } catch (IOException ignored) {
              }
            });
    responder.setDaemon(true);
    responder.start();
  }
}
