package ee.tuleva.onboarding.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminTokenValidatorTest {

  private static final String ADMIN_TOKEN = "admin-token";
  private static final String OPS_TOKEN = "ops-token";

  @Test
  void validateThrowsServiceUnavailableWhenAdminApiTokenIsNotConfigured() {
    AdminTokenValidator validator = new AdminTokenValidator("", OPS_TOKEN);

    assertThatThrownBy(() -> validator.validate("any-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }

  @Test
  void validateThrowsUnauthorizedWhenTokenDoesNotMatch() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN);

    assertThatThrownBy(() -> validator.validate("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validatePassesWhenTokenMatchesTheAdminApiToken() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN);

    assertThatCode(() -> validator.validate(ADMIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessPassesWhenTokenMatchesTheAdminApiToken() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN);

    assertThatCode(() -> validator.validateWithOpsAccess(ADMIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessPassesWhenTokenMatchesTheOpsToken() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN);

    assertThatCode(() -> validator.validateWithOpsAccess(OPS_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessThrowsUnauthorizedWhenTokenMatchesNeither() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN);

    assertThatThrownBy(() -> validator.validateWithOpsAccess("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validateWithOpsAccessThrowsUnauthorizedWhenOpsTokenIsNotConfigured() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, "");

    assertThatThrownBy(() -> validator.validateWithOpsAccess("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }
}
