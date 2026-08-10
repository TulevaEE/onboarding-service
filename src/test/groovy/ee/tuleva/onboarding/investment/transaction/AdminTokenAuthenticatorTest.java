package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminTokenAuthenticatorTest {

  @Test
  void resolvesTheOperatorBehindAPerOperatorToken() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties(
                "shared", Map.of("taavi", "taavi-token", "marju", "marju-token")));

    assertThat(authenticator.resolveActor("marju-token")).isEqualTo("marju");
  }

  @Test
  void resolvesTheSharedTokenToAnExplicitlyUnattributedActor() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties("shared", Map.of("taavi", "taavi-token")));

    assertThat(authenticator.resolveActor("shared")).isEqualTo("shared-admin-token");
  }

  @Test
  void rejectsAnUnknownToken() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties("shared", Map.of("taavi", "taavi-token")));

    assertThatThrownBy(() -> authenticator.resolveActor("nope"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void acceptsOperatorTokensEvenWhenNoSharedTokenIsConfigured() {
    var authenticator =
        new AdminTokenAuthenticator(new AdminTokenProperties("", Map.of("taavi", "taavi-token")));

    assertThat(authenticator.resolveActor("taavi-token")).isEqualTo("taavi");
  }

  @Test
  void reportsUnavailableWhenNoTokensAreConfiguredAtAll() {
    var authenticator = new AdminTokenAuthenticator(new AdminTokenProperties("", Map.of()));

    assertThatThrownBy(() -> authenticator.resolveActor("anything"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }

  @Test
  void refusesToStartWithABlankOperatorTokenThatWouldMatchAnEmptyHeader() {
    assertThatThrownBy(() -> new AdminTokenProperties("shared", Map.of("ghost", "")))
        .isInstanceOf(IllegalStateException.class);
  }
}
