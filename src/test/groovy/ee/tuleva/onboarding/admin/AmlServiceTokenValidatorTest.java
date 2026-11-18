package ee.tuleva.onboarding.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AmlServiceTokenValidatorTest {

  private static final String TOKEN = "aml-service-token";

  @Test
  void admitsTheAmlServicesBearerToken() {
    var validator = new AmlServiceTokenValidator(TOKEN);

    assertThatCode(() -> validator.validate("Bearer " + TOKEN)).doesNotThrowAnyException();
  }

  @Test
  void refusesAnotherToken() {
    var validator = new AmlServiceTokenValidator(TOKEN);

    assertThatThrownBy(() -> validator.validate("Bearer other-token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void refusesTheTokenWithoutTheBearerScheme() {
    var validator = new AmlServiceTokenValidator(TOKEN);

    assertThatThrownBy(() -> validator.validate(TOKEN))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void refusesEveryCallWhenNoTokenIsConfigured() {
    var validator = new AmlServiceTokenValidator("");

    assertThatThrownBy(() -> validator.validate("Bearer "))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }
}
