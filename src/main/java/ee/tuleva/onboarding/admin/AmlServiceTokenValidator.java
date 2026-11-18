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
public class AmlServiceTokenValidator {

  private static final String BEARER = "Bearer ";

  private final String amlServiceToken;

  public AmlServiceTokenValidator(@Value("${aml-service.api-token:}") String amlServiceToken) {
    this.amlServiceToken = amlServiceToken;
  }

  public void validate(String authorization) {
    if (amlServiceToken.isBlank()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AML service token not configured");
    }
    String expected = BEARER + amlServiceToken;
    if (!MessageDigest.isEqual(expected.getBytes(UTF_8), authorization.getBytes(UTF_8))) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid AML service token");
    }
  }
}
