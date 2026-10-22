package ee.tuleva.onboarding.auth.authority;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AuthorityTest {

  @Test
  void anAuthenticationCarryingTheConnectedAppAuthorityIsAConnectedApp() {
    var app =
        UsernamePasswordAuthenticationToken.authenticated(
            "person",
            null,
            List.of(
                new SimpleGrantedAuthority(CONNECTED_APP),
                new SimpleGrantedAuthority("SCOPE_balances:read")));

    assertThat(Authority.isConnectedApp(app)).isTrue();
  }

  @Test
  void aWebLoginIsNotAConnectedApp() {
    var webLogin =
        UsernamePasswordAuthenticationToken.authenticated(
            "person", "token", List.of(new SimpleGrantedAuthority(USER)));

    assertThat(Authority.isConnectedApp(webLogin)).isFalse();
  }

  @Test
  void noAuthenticationIsNotAConnectedApp() {
    assertThat(Authority.isConnectedApp(null)).isFalse();
  }

  @Test
  void anEs384TokenTypedAsAnAccessTokenIsAConnectedAppAccessToken() {
    assertThat(
            Authority.isConnectedAppAccessToken(
                tokenWithHeader("{\"alg\":\"ES384\",\"typ\":\"at+jwt\"}")))
        .isTrue();
  }

  @Test
  void aTokenTypedAsAnAccessTokenButSignedAnotherWayIsNotAConnectedAppAccessToken() {
    assertThat(
            Authority.isConnectedAppAccessToken(
                tokenWithHeader("{\"alg\":\"RS256\",\"typ\":\"at+jwt\"}")))
        .isFalse();
  }

  @Test
  void anEs384TokenWithoutTheAccessTokenTypeIsNotAConnectedAppAccessToken() {
    assertThat(Authority.isConnectedAppAccessToken(tokenWithHeader("{\"alg\":\"ES384\"}")))
        .isFalse();
  }

  @Test
  void somethingThatIsNotASignedTokenIsNotAConnectedAppAccessToken() {
    assertThat(Authority.isConnectedAppAccessToken("not-a-token")).isFalse();
  }

  private static String tokenWithHeader(String header) {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(header.getBytes(UTF_8))
        + "."
        + encoder.encodeToString("{}".getBytes(UTF_8))
        + "."
        + encoder.encodeToString("signature".getBytes(UTF_8));
  }
}
