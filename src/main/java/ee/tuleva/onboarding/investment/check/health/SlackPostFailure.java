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

enum SlackPostFailure {
  NOT_SENT,
  PAYLOAD_REFUSED,
  UNCERTAIN;

  private static final Set<String> SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF =
      Set.of("invalid_payload", "no_text", "too_many_attachments");
  private static final List<HttpStatus> SLACK_REFUSALS_THAT_POST_NOTHING =
      List.of(BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, GONE, TOO_MANY_REQUESTS);
  private static final List<Class<? extends Exception>> FAILURES_BEFORE_THE_REQUEST_WENT_OUT =
      List.of(
          ConnectException.class,
          NoRouteToHostException.class,
          UnknownHostException.class,
          ConnectTimeoutException.class,
          HttpConnectTimeoutException.class,
          IllegalStateException.class);
  private static final Pattern URL_THAT_CAN_CARRY_THE_WEBHOOK_SECRET =
      Pattern.compile("https?://\\S+");

  static SlackPostFailure of(RuntimeException failure) {
    if (failure instanceof HttpClientErrorException refusal) {
      return ofRefusal(refusal);
    }
    return causes(failure).anyMatch(SlackPostFailure::happensBeforeTheRequestGoesOut)
        ? NOT_SENT
        : UNCERTAIN;
  }

  static String describe(RuntimeException failure) {
    var cause = NestedExceptionUtils.getMostSpecificCause(failure);
    return URL_THAT_CAN_CARRY_THE_WEBHOOK_SECRET
        .matcher(cause.getClass().getName() + ": " + cause.getMessage())
        .replaceAll("<url>");
  }

  private static SlackPostFailure ofRefusal(HttpClientErrorException refusal) {
    if (refusesThePayloadItself(refusal)) {
      return PAYLOAD_REFUSED;
    }
    return SLACK_REFUSALS_THAT_POST_NOTHING.stream()
            .anyMatch(status -> status.isSameCodeAs(refusal.getStatusCode()))
        ? NOT_SENT
        : UNCERTAIN;
  }

  private static boolean refusesThePayloadItself(HttpClientErrorException refusal) {
    return refusal.getStatusCode().isSameCodeAs(CONTENT_TOO_LARGE)
        || (refusal.getStatusCode().isSameCodeAs(BAD_REQUEST)
            && SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF.contains(
                refusal.getResponseBodyAsString().strip()));
  }

  private static Stream<Throwable> causes(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
  }

  private static boolean happensBeforeTheRequestGoesOut(Throwable cause) {
    return FAILURES_BEFORE_THE_REQUEST_WENT_OUT.stream().anyMatch(type -> type.isInstance(cause));
  }
}
