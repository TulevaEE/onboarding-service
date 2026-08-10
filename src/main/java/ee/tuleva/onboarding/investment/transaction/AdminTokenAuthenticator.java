package ee.tuleva.onboarding.investment.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@NullMarked
public class AdminTokenAuthenticator {

  static final String SHARED_TOKEN_ACTOR = "shared-admin-token";

  private final AdminTokenProperties properties;

  public String resolveActor(String token) {
    if (properties.apiToken().isBlank() && properties.operatorTokens().isEmpty()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    return operatorFor(token)
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid admin token"));
  }

  private Optional<String> operatorFor(String token) {
    return properties.operatorTokens().entrySet().stream()
        .filter(operator -> matches(operator.getValue(), token))
        .map(Map.Entry::getKey)
        .findFirst()
        .or(() -> sharedTokenActor(token));
  }

  private Optional<String> sharedTokenActor(String token) {
    return !properties.apiToken().isBlank() && matches(properties.apiToken(), token)
        ? Optional.of(SHARED_TOKEN_ACTOR)
        : Optional.empty();
  }

  private static boolean matches(String configured, String presented) {
    return MessageDigest.isEqual(configured.getBytes(UTF_8), presented.getBytes(UTF_8));
  }
}
