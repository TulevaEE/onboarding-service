package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.failed;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.unknown;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.google.gson.Gson;
import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public final class AtMostOnceEmailSender {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(200);
  private static final List<Class<? extends Throwable>> FAILURES_BEFORE_REACHING_MANDRILL =
      List.of(
          ConnectException.class, UnknownHostException.class, HttpConnectTimeoutException.class);
  private static final Set<String> MANDRILL_ERRORS_BEFORE_SENDING =
      Set.of("Invalid_Key", "PaymentRequired", "Unknown_Subaccount", "ValidationError");
  private static final Gson MANDRILL_JSON = LutungGsonUtils.getGson();

  private final @Nullable String key;
  private final RestClient restClient;
  private final RetryTemplate retryUntilMandrillIsReached;

  AtMostOnceEmailSender(@Nullable String key, RestClient restClient, Duration firstRetryDelay) {
    this.key = key;
    this.restClient = restClient;
    this.retryUntilMandrillIsReached =
        new RetryTemplate(
            RetryPolicy.builder()
                .predicate(AtMostOnceEmailSender::neverReachedMandrill)
                .maxRetries(2)
                .delay(firstRetryDelay)
                .multiplier(3)
                .maxDelay(Duration.ofSeconds(2))
                .build());
  }

  public static AtMostOnceEmailSender using(String key, URI mandrillApiUrl) {
    return new AtMostOnceEmailSender(
        key, restClient(mandrillApiUrl, CONNECT_TIMEOUT, ANSWER_TIMEOUT), FIRST_RETRY_DELAY);
  }

  public static AtMostOnceEmailSender withoutKey() {
    return new AtMostOnceEmailSender(null, RestClient.create(), FIRST_RETRY_DELAY);
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

  public EmailSendResult send(MandrillMessage message) {
    if (key == null) {
      return logged(failed(List.of(), "No Mandrill key for this sender, nothing was sent"));
    }
    return logged(send(key, message));
  }

  private EmailSendResult send(String key, MandrillMessage message) {
    String request;
    try {
      request = MANDRILL_JSON.toJson(Map.of("key", key, "message", message, "async", false));
    } catch (RuntimeException e) {
      return failed(
          List.of(), "The message could not be serialised, nothing was sent: " + describe(e));
    }
    try {
      @Nullable String answer = retryUntilMandrillIsReached.execute(() -> post(request));
      return resultOf(message, answer);
    } catch (RetryException e) {
      return resultOf(e.getCause());
    }
  }

  private @Nullable String post(String request) {
    return restClient
        .post()
        .uri("/messages/send.json")
        .contentType(APPLICATION_JSON)
        .body(request.getBytes(UTF_8))
        .retrieve()
        .body(String.class);
  }

  private static EmailSendResult resultOf(MandrillMessage message, @Nullable String answer) {
    try {
      MandrillMessageStatus[] statuses =
          answer == null ? null : MANDRILL_JSON.fromJson(answer, MandrillMessageStatus[].class);
      if (statuses == null) {
        return unknown(List.of(), "Mandrill answered with an empty body");
      }
      return MandrillRecipientStatuses.of(message, statuses).result();
    } catch (RuntimeException e) {
      return unknown(List.of(), "Mandrill's answer could not be read: " + describe(e));
    }
  }

  private static EmailSendResult resultOf(Throwable failure) {
    if (failure instanceof RestClientResponseException response) {
      return resultOfRefusal(response);
    }
    return causes(failure)
        .filter(AtMostOnceEmailSender::isFailureBeforeReachingMandrill)
        .findFirst()
        .map(cause -> failed(List.of(), "Mandrill could not be reached: " + describe(cause)))
        .orElseGet(() -> unknown(List.of(), unknownFailure(failure)));
  }

  private static String unknownFailure(Throwable failure) {
    return causes(failure)
        .filter(HttpTimeoutException.class::isInstance)
        .findFirst()
        .map(timeout -> "No answer from Mandrill in time: " + describe(timeout))
        .orElseGet(() -> "Mandrill call failed: " + describe(rootCause(failure)));
  }

  private static EmailSendResult resultOfRefusal(RestClientResponseException response) {
    var error = MandrillError.read(response.getResponseBodyAsString());
    var problem =
        "Mandrill refused the request: httpStatus=%d, name=%s, message=%s"
            .formatted(response.getStatusCode().value(), error.name(), error.message());
    return error.wasRaisedBeforeSending()
        ? failed(List.of(), problem)
        : unknown(List.of(), problem);
  }

  private static boolean neverReachedMandrill(Throwable failure) {
    return causes(failure).anyMatch(AtMostOnceEmailSender::isFailureBeforeReachingMandrill);
  }

  private static boolean isFailureBeforeReachingMandrill(Throwable cause) {
    return FAILURES_BEFORE_REACHING_MANDRILL.stream().anyMatch(type -> type.isInstance(cause));
  }

  private static Throwable rootCause(Throwable failure) {
    return causes(failure).reduce((outer, inner) -> inner).orElse(failure);
  }

  private static Stream<Throwable> causes(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
  }

  private static String describe(Throwable failure) {
    return failure.getMessage() == null
        ? failure.getClass().getSimpleName()
        : failure.getClass().getSimpleName() + ": " + failure.getMessage();
  }

  private static EmailSendResult logged(EmailSendResult result) {
    if (result.outcome() != SENT) {
      log.error(
          "Email not confirmed as sent by Mandrill, not sent again: outcome={}, problem={}",
          result.outcome(),
          result.problem());
    }
    return result;
  }

  private record MandrillError(@Nullable String name, @Nullable String message) {

    static MandrillError read(String answer) {
      try {
        var error = MANDRILL_JSON.fromJson(answer, MandrillError.class);
        return error == null ? new MandrillError(null, null) : error;
      } catch (RuntimeException e) {
        return new MandrillError(null, null);
      }
    }

    boolean wasRaisedBeforeSending() {
      var errorName = name;
      return errorName != null && MANDRILL_ERRORS_BEFORE_SENDING.contains(errorName);
    }
  }
}
