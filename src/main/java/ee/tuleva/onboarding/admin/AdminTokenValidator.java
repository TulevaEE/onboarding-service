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

  public AdminTokenValidator(
      @Value("${admin.api-token:}") String adminApiToken,
      @Value("${admin.ops-token:}") String opsToken) {
    this.adminApiToken = adminApiToken;
    this.opsToken = opsToken;
  }

  public void validate(String token) {
    if (adminApiToken.isBlank()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    if (!MessageDigest.isEqual(adminApiToken.getBytes(UTF_8), token.getBytes(UTF_8))) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
  }

  public void validateWithOpsAccess(String token) {
    boolean matchesAdmin =
        !adminApiToken.isBlank()
            && MessageDigest.isEqual(adminApiToken.getBytes(UTF_8), token.getBytes(UTF_8));
    boolean matchesOps =
        !opsToken.isBlank()
            && MessageDigest.isEqual(opsToken.getBytes(UTF_8), token.getBytes(UTF_8));
    if (!matchesAdmin && !matchesOps) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
  }
}
