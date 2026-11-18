package ee.tuleva.onboarding.investment.transaction.export;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

class GoogleDriveConfigurationTest {

  private static final String TOKEN_HOST = "oauth2.googleapis.com";
  private static final String DRIVE_HOST = "www.googleapis.com";

  private ServerSocket unresponsiveServer;
  private HttpServer tokenServer;
  private final AtomicInteger tokenRequests = new AtomicInteger();

  @BeforeEach
  void startServers() throws IOException {
    unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    tokenServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    tokenServer.createContext(
        "/token",
        exchange -> {
          tokenRequests.incrementAndGet();
          var response =
              "{\"access_token\":\"synthetic-token\",\"expires_in\":3600}".getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    tokenServer.start();
  }

  @AfterEach
  void stopServers() throws IOException {
    tokenServer.stop(0);
    unresponsiveServer.close();
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpOnAGoogleTokenEndpointThatDoesNotAnswerWithinTheReadTimeout() {
    contextRunner(Map.of(TOKEN_HOST, unresponsiveServerUri()))
        .run(
            context -> {
              var tokenProvider = context.getBean(GoogleDriveTokenProvider.class);

              Throwable thrown = catchThrowable(tokenProvider::getAccessToken);

              assertThat(thrown)
                  .isInstanceOf(ResourceAccessException.class)
                  .hasRootCauseInstanceOf(SocketTimeoutException.class);
            });
  }

  @Test
  @Timeout(value = 10, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void givesUpOnAGoogleDriveCallThatDoesNotAnswerWithinTheReadTimeout() {
    contextRunner(Map.of(TOKEN_HOST, tokenServerUri(), DRIVE_HOST, unresponsiveServerUri()))
        .run(
            context -> {
              var driveClient = context.getBean(GoogleDriveClient.class);

              Throwable thrown = catchThrowable(() -> driveClient.findFolder("parent", "2026-11"));

              assertThat(thrown)
                  .isInstanceOf(ResourceAccessException.class)
                  .hasRootCauseInstanceOf(SocketTimeoutException.class);
              assertThat(tokenRequests).hasValue(1);
            });
  }

  private ApplicationContextRunner contextRunner(Map<String, URI> redirects) {
    return new ApplicationContextRunner()
        .withUserConfiguration(GoogleDriveConfiguration.class)
        .withConfiguration(
            AutoConfigurations.of(
                HttpClientAutoConfiguration.class,
                ImperativeHttpClientAutoConfiguration.class,
                RestClientAutoConfiguration.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(RestClientCustomizer.class, () -> redirecting(redirects))
        .withPropertyValues(
            "google-drive.enabled=true",
            "google-drive.service-account-json=" + syntheticServiceAccountJson(),
            "google-drive.root-folder-id=root",
            "spring.http.clients.connect-timeout=500ms",
            "spring.http.clients.read-timeout=250ms");
  }

  private static RestClientCustomizer redirecting(Map<String, URI> redirects) {
    return builder ->
        builder.requestInterceptor(
            (request, body, execution) -> execution.execute(redirected(request, redirects), body));
  }

  private static HttpRequest redirected(HttpRequest request, Map<String, URI> redirects) {
    var original = request.getURI();
    var target = redirects.getOrDefault(original.getHost(), original);
    var uri =
        UriComponentsBuilder.fromUri(original)
            .scheme(target.getScheme())
            .host(target.getHost())
            .port(target.getPort())
            .build(true)
            .toUri();
    return new HttpRequestWrapper(request) {
      @Override
      public URI getURI() {
        return uri;
      }
    };
  }

  private URI unresponsiveServerUri() {
    return URI.create("http://127.0.0.1:" + unresponsiveServer.getLocalPort());
  }

  private URI tokenServerUri() {
    return URI.create("http://127.0.0.1:" + tokenServer.getAddress().getPort());
  }

  private static String syntheticServiceAccountJson() {
    var privateKey = Base64.getEncoder().encodeToString(generatedPrivateKey());
    var json =
        "{\"client_email\":\"test@project.iam.gserviceaccount.com\",\"private_key\":\"-----BEGIN"
            + " PRIVATE KEY-----"
            + privateKey
            + "-----END PRIVATE KEY-----\"}";
    return Base64.getEncoder().encodeToString(json.getBytes(UTF_8));
  }

  private static byte[] generatedPrivateKey() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair().getPrivate().getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
