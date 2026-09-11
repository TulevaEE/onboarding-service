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
  private static final String READ_TOKEN = "read-token";

  private final AdminTokenValidator validator =
      new AdminTokenValidator(ADMIN_TOKEN, OPS_TOKEN, READ_TOKEN);

  @Test
  void validateThrowsServiceUnavailableWhenAdminApiTokenIsNotConfigured() {
    AdminTokenValidator validator = new AdminTokenValidator("", OPS_TOKEN, READ_TOKEN);

    assertThatThrownBy(() -> validator.validate("any-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }

  @Test
  void validateThrowsUnauthorizedWhenTokenDoesNotMatch() {
    assertThatThrownBy(() -> validator.validate("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validatePassesWhenTokenMatchesTheAdminApiToken() {
    assertThatCode(() -> validator.validate(ADMIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessPassesWhenTokenMatchesTheAdminApiToken() {
    assertThatCode(() -> validator.validateWithOpsAccess(ADMIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessPassesWhenTokenMatchesTheOpsToken() {
    assertThatCode(() -> validator.validateWithOpsAccess(OPS_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateWithOpsAccessThrowsUnauthorizedWhenTokenMatchesNeither() {
    assertThatThrownBy(() -> validator.validateWithOpsAccess("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validateWithOpsAccessThrowsUnauthorizedWhenOpsTokenIsNotConfigured() {
    AdminTokenValidator validator = new AdminTokenValidator(ADMIN_TOKEN, "", READ_TOKEN);

    assertThatThrownBy(() -> validator.validateWithOpsAccess("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validateReadAccessPassesWhenTokenMatchesTheReadToken() {
    assertThatCode(() -> validator.validateReadAccess(READ_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateReadAccessPassesWhenTokenMatchesTheAdminApiToken() {
    assertThatCode(() -> validator.validateReadAccess(ADMIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void validateReadAccessThrowsUnauthorizedWhenTokenMatchesNeither() {
    assertThatThrownBy(() -> validator.validateReadAccess("wrong-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void theReadTokenOpensNothingBeyondReading() {
    assertThatThrownBy(() -> validator.validate(READ_TOKEN))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);

    assertThatThrownBy(() -> validator.validateWithOpsAccess(READ_TOKEN))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void validateReadAccessThrowsServiceUnavailableWhenNoReadCredentialIsConfigured() {
    AdminTokenValidator validator = new AdminTokenValidator("", OPS_TOKEN, "");

    assertThatThrownBy(() -> validator.validateReadAccess("any-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }
}
