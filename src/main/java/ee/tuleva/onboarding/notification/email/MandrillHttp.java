package ee.tuleva.onboarding.notification.email;

import static java.net.http.HttpClient.Version.HTTP_1_1;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class MandrillHttp {

  private static final Duration CONNECT_TIMEOUT_FOR_BACKGROUND_JOB = Duration.ofSeconds(10);
  private static final Duration ANSWER_TIMEOUT_FOR_BACKGROUND_JOB = Duration.ofSeconds(30);
  private static final List<Class<? extends Throwable>> FAILURES_BEFORE_REACHING_MANDRILL =
      List.of(
          ConnectException.class, UnknownHostException.class, HttpConnectTimeoutException.class);

  private MandrillHttp() {}

  static RestClient restClientForBackgroundJob(URI mandrillApiUrl) {
    return restClient(
        mandrillApiUrl, CONNECT_TIMEOUT_FOR_BACKGROUND_JOB, ANSWER_TIMEOUT_FOR_BACKGROUND_JOB);
  }

  static RestClient restClient(
      URI mandrillApiUrl, Duration connectTimeout, Duration answerTimeout) {
    var httpClient =
        HttpClient.newBuilder().version(HTTP_1_1).connectTimeout(connectTimeout).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(answerTimeout);
    return RestClient.builder()
        .baseUrl(mandrillApiUrl.toString())
        .requestFactory(requestFactory)
        .build();
  }

  static boolean neverReachedMandrill(Throwable failure) {
    return causes(failure).anyMatch(MandrillHttp::isFailureBeforeReachingMandrill);
  }

  static boolean isFailureBeforeReachingMandrill(Throwable cause) {
    return FAILURES_BEFORE_REACHING_MANDRILL.stream().anyMatch(type -> type.isInstance(cause));
  }

  static Stream<Throwable> causes(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
  }
}
