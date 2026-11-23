package ee.tuleva.onboarding.investment.check.health;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

enum SlackPostFailure {
  NOT_SENT,
  PAYLOAD_REFUSED,
  UNCERTAIN;

  private static final Set<String> SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF =
      Set.of("invalid_payload", "no_text", "too_many_attachments");
  private static final Set<String> SLACK_WEBHOOK_ERRORS_ABOUT_ITS_SETUP_NOT_THE_PAYLOAD =
      Set.of(
          "no_team",
          "no_service_id",
          "invalid_token",
          "no_active_hooks",
          "no_service",
          "team_disabled",
          "channel_not_found",
          "channel_is_archived",
          "action_prohibited",
          "posting_to_general_channel_denied",
          "user_not_found");
  private static final List<HttpStatus> SLACK_REFUSALS_THAT_POST_NOTHING =
      List.of(UNAUTHORIZED, FORBIDDEN, NOT_FOUND, GONE, TOO_MANY_REQUESTS);
  private static final List<Class<? extends Exception>> FAILURES_BEFORE_THE_REQUEST_WENT_OUT =
      List.of(
          ConnectException.class,
          NoRouteToHostException.class,
          UnknownHostException.class,
          ConnectTimeoutException.class,
          HttpConnectTimeoutException.class);
  private static final Pattern URL_THAT_CAN_CARRY_THE_WEBHOOK_SECRET =
      Pattern.compile("https?://\\S+");
  private static final int BODY_CHARACTERS_LOGGED_ENOUGH_FOR_A_SLACK_ERROR_CODE = 200;

  static SlackPostFailure of(RuntimeException failure) {
    if (failure instanceof HttpClientErrorException refusal) {
      return ofRefusal(refusal);
    }
    if (isNoWebhookConfiguredForTheChannel(failure)) {
      return NOT_SENT;
    }
    return causes(failure).anyMatch(SlackPostFailure::happensBeforeTheRequestGoesOut)
        ? NOT_SENT
        : UNCERTAIN;
  }

  static String describe(RuntimeException failure) {
    if (failure instanceof HttpStatusCodeException answer) {
      return withoutUrls(
          "%s: %d %s"
              .formatted(
                  answer.getClass().getName(), answer.getStatusCode().value(), bodyOf(answer)));
    }
    var cause = NestedExceptionUtils.getMostSpecificCause(failure);
    return withoutUrls(cause.getClass().getName() + ": " + cause.getMessage());
  }

  private static boolean isNoWebhookConfiguredForTheChannel(RuntimeException failure) {
    return failure instanceof IllegalStateException;
  }

  private static SlackPostFailure ofRefusal(HttpClientErrorException refusal) {
    if (refusal.getStatusCode().isSameCodeAs(CONTENT_TOO_LARGE)) {
      return PAYLOAD_REFUSED;
    }
    if (refusal.getStatusCode().isSameCodeAs(BAD_REQUEST)) {
      return ofBadRequest(refusal.getResponseBodyAsString().strip());
    }
    return SLACK_REFUSALS_THAT_POST_NOTHING.stream()
            .anyMatch(status -> status.isSameCodeAs(refusal.getStatusCode()))
        ? NOT_SENT
        : UNCERTAIN;
  }

  private static SlackPostFailure ofBadRequest(String body) {
    if (SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF.contains(body)) {
      return PAYLOAD_REFUSED;
    }
    return SLACK_WEBHOOK_ERRORS_ABOUT_ITS_SETUP_NOT_THE_PAYLOAD.contains(body)
        ? NOT_SENT
        : UNCERTAIN;
  }

  private static String bodyOf(HttpStatusCodeException answer) {
    var body = answer.getResponseBodyAsString().strip();
    return body.length() <= BODY_CHARACTERS_LOGGED_ENOUGH_FOR_A_SLACK_ERROR_CODE
        ? body
        : body.substring(0, BODY_CHARACTERS_LOGGED_ENOUGH_FOR_A_SLACK_ERROR_CODE) + "...";
  }

  private static String withoutUrls(String text) {
    return URL_THAT_CAN_CARRY_THE_WEBHOOK_SECRET.matcher(text).replaceAll("<url>");
  }

  private static Stream<Throwable> causes(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
  }

  private static boolean happensBeforeTheRequestGoesOut(Throwable cause) {
    return FAILURES_BEFORE_THE_REQUEST_WENT_OUT.stream().anyMatch(type -> type.isInstance(cause));
  }
}
