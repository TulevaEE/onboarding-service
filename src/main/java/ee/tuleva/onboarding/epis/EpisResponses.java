package ee.tuleva.onboarding.epis;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

final class EpisResponses {

  static <T> T requireBody(@Nullable T body, String endpoint) {
    return requireNonNull(body, "EPIS response body missing: endpoint=" + endpoint);
  }

  static <T> T requireBody(ResponseEntity<T> response, String endpoint) {
    return requireBody(response.getBody(), endpoint);
  }

  private EpisResponses() {}
}
