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
                "shared", "ops-token", Map.of("taavi", "taavi-token", "marju", "marju-token")));

    assertThat(authenticator.resolveActor("marju-token")).isEqualTo("marju");
  }

  @Test
  void resolvesTheSharedTokenToAnExplicitlyUnattributedActor() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties("shared", "ops-token", Map.of("taavi", "taavi-token")));

    assertThat(authenticator.resolveActor("shared")).isEqualTo("shared-admin-token");
  }

  @Test
  void countsOnlyOperatorsWithTheirOwnTokenAsNamed() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties(
                "shared", "ops-token", Map.of("operator-1", "operator-1-token")));

    assertThat(authenticator.isNamedOperator("operator-1")).isTrue();
    assertThat(authenticator.isNamedOperator("shared-admin-token")).isFalse();
    assertThat(authenticator.isNamedOperator("system")).isFalse();
  }

  @Test
  void refusesToStartWithAnOperatorTokenThatIsAlsoTheSharedTokenSoOnePersonCannotActAsTwo() {
    assertThatThrownBy(
            () -> new AdminTokenProperties("shared", "ops-token", Map.of("operator-1", "shared")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesToStartWithAnOperatorTokenThatIsAlsoTheOpsToken() {
    assertThatThrownBy(() -> new AdminTokenProperties("shared", "ops", Map.of("operator-1", "ops")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesToStartWithAnOperatorNamedLikeTheSharedTokensActor() {
    assertThatThrownBy(
            () ->
                new AdminTokenProperties(
                    "shared", "ops", Map.of("shared-admin-token", "own-token")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesToStartWithTwoOperatorsSharingATokenSoEachTokenNamesOnePerson() {
    assertThatThrownBy(
            () ->
                new AdminTokenProperties(
                    "shared", "ops-token", Map.of("operator-1", "same", "operator-7", "same")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsAnUnknownToken() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties("shared", "ops-token", Map.of("taavi", "taavi-token")));

    assertThatThrownBy(() -> authenticator.resolveActor("nope"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(UNAUTHORIZED);
  }

  @Test
  void acceptsOperatorTokensEvenWhenNoSharedTokenIsConfigured() {
    var authenticator =
        new AdminTokenAuthenticator(
            new AdminTokenProperties("", "ops-token", Map.of("taavi", "taavi-token")));

    assertThat(authenticator.resolveActor("taavi-token")).isEqualTo("taavi");
  }

  @Test
  void reportsUnavailableWhenNoTokensAreConfiguredAtAll() {
    var authenticator =
        new AdminTokenAuthenticator(new AdminTokenProperties("", "ops-token", Map.of()));

    assertThatThrownBy(() -> authenticator.resolveActor("anything"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }

  // Neither property is set in local runs, and an unconfigured admin API has to say so rather
  // than blow up on a null token.
  @Test
  void treatsCompletelyUnsetPropertiesAsNoTokensRatherThanNulls() {
    var properties = new AdminTokenProperties(null, null, null);

    assertThat(properties.apiToken()).isEmpty();
    assertThat(properties.opsToken()).isEmpty();
    assertThat(properties.operatorTokens()).isEmpty();
    assertThatThrownBy(() -> new AdminTokenAuthenticator(properties).resolveActor("anything"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(SERVICE_UNAVAILABLE);
  }

  @Test
  void refusesToStartWithABlankOperatorTokenThatWouldMatchAnEmptyHeader() {
    assertThatThrownBy(() -> new AdminTokenProperties("shared", "ops-token", Map.of("ghost", "")))
        .isInstanceOf(IllegalStateException.class);
  }
}
