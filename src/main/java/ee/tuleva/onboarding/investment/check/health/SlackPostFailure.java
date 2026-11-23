package ee.tuleva.onboarding.investment.check.health;

import static java.util.Locale.ROOT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.web.client.HttpClientErrorException;

enum SlackPostFailure {
  NO_ANSWER,
  PAYLOAD_REFUSED,
  NOT_TAKEN;

  private static final Set<String> SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF =
      Set.of("invalid_payload", "no_text", "too_many_attachments");

  static SlackPostFailure of(RuntimeException failure) {
    if (failure instanceof HttpClientErrorException refusal) {
      return refusesThePayloadItself(refusal) ? PAYLOAD_REFUSED : NOT_TAKEN;
    }
    return Stream.<Throwable>iterate(failure, Objects::nonNull, Throwable::getCause)
            .anyMatch(SlackPostFailure::isTimeoutWaitingForTheAnswer)
        ? NO_ANSWER
        : NOT_TAKEN;
  }

  int unansweredPosts() {
    return this == NO_ANSWER ? 1 : 0;
  }

  private static boolean refusesThePayloadItself(HttpClientErrorException refusal) {
    return refusal.getStatusCode().isSameCodeAs(CONTENT_TOO_LARGE)
        || (refusal.getStatusCode().isSameCodeAs(BAD_REQUEST)
            && SLACK_ERRORS_ABOUT_THE_PAYLOAD_ITSELF.contains(
                refusal.getResponseBodyAsString().strip()));
  }

  private static boolean isTimeoutWaitingForTheAnswer(Throwable cause) {
    if (cause instanceof HttpTimeoutException timeout) {
      return !(timeout instanceof HttpConnectTimeoutException);
    }
    return cause instanceof SocketTimeoutException timeout && !isAboutConnecting(timeout);
  }

  private static boolean isAboutConnecting(SocketTimeoutException timeout) {
    return String.valueOf(timeout.getMessage()).toLowerCase(ROOT).contains("connect");
  }
}
