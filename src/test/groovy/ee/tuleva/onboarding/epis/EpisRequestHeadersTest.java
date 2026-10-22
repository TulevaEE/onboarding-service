package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.auth.ServiceTokenProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class EpisRequestHeadersTest {

  @Mock ServiceTokenProvider serviceTokenProvider;

  EpisRequestHeaders episRequestHeaders;

  @BeforeEach
  void setUp() {
    episRequestHeaders = new EpisRequestHeaders(serviceTokenProvider);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void forToken_setsJsonContentTypeAndBearerAuthorizationHeader() {
    HttpHeaders headers = episRequestHeaders.forToken("some-token");

    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(headers.get("Authorization")).containsExactly("Bearer some-token");
  }

  @Test
  void entityFor_wrapsTokenInEntityWithHeaders() {
    var entity = episRequestHeaders.entityFor("some-token");

    assertThat(entity.getBody()).isNull();
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getHeaders().get("Authorization")).containsExactly("Bearer some-token");
  }

  @Test
  void forwardsTheWebLoginTokenOfThePersonLoggedIn() {
    authenticate(
        UsernamePasswordAuthenticationToken.authenticated(
            "person", "web-login-token", List.of(new SimpleGrantedAuthority(USER))));

    assertThat(episRequestHeaders.user().get("Authorization"))
        .containsExactly("Bearer web-login-token");
  }

  @Test
  void neverBuildsARequestToPensionikeskusWhileAConnectedAppIsAuthenticated() {
    authenticate(
        UsernamePasswordAuthenticationToken.authenticated(
            "person",
            null,
            List.of(
                new SimpleGrantedAuthority(CONNECTED_APP),
                new SimpleGrantedAuthority("SCOPE_balances:read"))));

    assertThatThrownBy(() -> episRequestHeaders.userEntity())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> episRequestHeaders.user()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> episRequestHeaders.service())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> episRequestHeaders.entityFor("any-token"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aConnectedAppIsRefusedEvenIfItsAuthenticationEverCarriedCredentials() {
    authenticate(
        UsernamePasswordAuthenticationToken.authenticated(
            "person", "some-token", List.of(new SimpleGrantedAuthority(CONNECTED_APP))));

    assertThatThrownBy(() -> episRequestHeaders.userJwtToken())
        .isInstanceOf(IllegalStateException.class);
  }

  private static void authenticate(UsernamePasswordAuthenticationToken authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
