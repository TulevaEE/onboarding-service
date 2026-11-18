package ee.tuleva.onboarding.party;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClientResponseException;

// What the population register said of a failed query, for the log: the status and the register's
// own error code, title and trace id, never its detail or the request, which can carry codes.
record RegisterFailure(
    String error,
    Optional<Integer> status,
    Optional<String> code,
    Optional<String> title,
    Optional<String> traceId) {

  static RegisterFailure of(RuntimeException exception) {
    Optional<RestClientResponseException> response = responseOf(exception);
    String body = response.map(RestClientResponseException::getResponseBodyAsString).orElse("");
    return new RegisterFailure(
        exception.getClass().getSimpleName(),
        response.map(answer -> answer.getStatusCode().value()),
        field(body, "code"),
        field(body, "title"),
        field(body, "traceId"));
  }

  @Override
  public String toString() {
    return "error="
        + error
        + ", status="
        + status.map(String::valueOf).orElse("none")
        + ", code="
        + code.orElse("none")
        + ", title="
        + title.orElse("none")
        + ", traceId="
        + traceId.orElse("none");
  }

  private static Optional<RestClientResponseException> responseOf(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof RestClientResponseException response) {
        return Optional.of(response);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> field(String body, String name) {
    Matcher matcher =
        Pattern.compile("\"" + name + "\"\\s*:\\s*\"([A-Za-z0-9_ .:-]{1,120})\"").matcher(body);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
