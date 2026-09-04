package ee.tuleva.onboarding.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.security.MessageDigest;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@NullMarked
@Component
public class AdminTokenValidator {

  private final String adminApiToken;
  private final String opsToken;
  private final String readToken;

  public AdminTokenValidator(
      @Value("${admin.api-token:}") String adminApiToken,
      @Value("${admin.ops-token:}") String opsToken,
      @Value("${admin.read-token:}") String readToken) {
    this.adminApiToken = adminApiToken;
    this.opsToken = opsToken;
    this.readToken = readToken;
  }

  public void validate(String token) {
    if (adminApiToken.isBlank()) {
      throw notConfigured();
    }
    if (!matches(adminApiToken, token)) {
      throw unauthorized();
    }
  }

  public void validateWithOpsAccess(String token) {
    if (!matches(adminApiToken, token) && !matches(opsToken, token)) {
      throw unauthorized();
    }
  }

  public void validateReadAccess(String token) {
    if (readToken.isBlank() && adminApiToken.isBlank()) {
      throw notConfigured();
    }
    if (!matches(readToken, token) && !matches(adminApiToken, token)) {
      throw unauthorized();
    }
  }

  private static boolean matches(String configured, String presented) {
    return !configured.isBlank()
        && MessageDigest.isEqual(configured.getBytes(UTF_8), presented.getBytes(UTF_8));
  }

  private static ResponseStatusException notConfigured() {
    return new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
  }

  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
  }
}
