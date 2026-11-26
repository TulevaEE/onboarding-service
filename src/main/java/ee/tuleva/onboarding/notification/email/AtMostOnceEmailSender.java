package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.failed;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.unknown;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.google.gson.Gson;
import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public final class AtMostOnceEmailSender {

  private static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(200);
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
                .predicate(MandrillHttp::neverReachedMandrill)
                .maxRetries(2)
                .delay(firstRetryDelay)
                .multiplier(3)
                .maxDelay(Duration.ofSeconds(2))
                .build());
  }

  public static AtMostOnceEmailSender using(String key, URI mandrillApiUrl) {
    return new AtMostOnceEmailSender(
        key, MandrillHttp.restClientForBackgroundJob(mandrillApiUrl), FIRST_RETRY_DELAY);
  }

  public static AtMostOnceEmailSender withoutKey() {
    return new AtMostOnceEmailSender(null, RestClient.create(), FIRST_RETRY_DELAY);
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
    return MandrillHttp.causes(failure)
        .filter(MandrillHttp::isFailureBeforeReachingMandrill)
        .findFirst()
        .map(cause -> failed(List.of(), "Mandrill could not be reached: " + describe(cause)))
        .orElseGet(() -> unknown(List.of(), unknownFailure(failure)));
  }

  private static String unknownFailure(Throwable failure) {
    return MandrillHttp.causes(failure)
        .filter(HttpTimeoutException.class::isInstance)
        .findFirst()
        .map(timeout -> "No answer from Mandrill in time: " + describe(timeout))
        .orElseGet(() -> "Mandrill call failed: " + describe(rootCause(failure)));
  }

  private static EmailSendResult resultOfRefusal(RestClientResponseException response) {
    var error =
        MandrillError.read(response.getStatusCode().value(), response.getResponseBodyAsString());
    var problem =
        "Mandrill refused the request: httpStatus=%d, name=%s, message=%s"
            .formatted(error.httpStatus(), error.name(), error.message());
    return error.wasRaisedBeforeSending()
        ? failed(List.of(), problem)
        : unknown(List.of(), problem);
  }

  private static Throwable rootCause(Throwable failure) {
    return MandrillHttp.causes(failure).reduce((outer, inner) -> inner).orElse(failure);
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
}
