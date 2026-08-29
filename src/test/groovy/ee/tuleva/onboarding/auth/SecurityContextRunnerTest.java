package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityContextRunnerTest {

  @Mock private PrincipalService principalService;
  @Mock private GrantedAuthorityFactory grantedAuthorityFactory;
  @Mock private JwtTokenUtil jwtTokenUtil;
  @InjectMocks private SecurityContextRunner securityContextRunner;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void runsTheActionWithTheUserAuthenticatedAndClearsTheContextAfter() {
    User user = sampleUser().build();
    AuthenticatedPerson principal =
        AuthenticatedPerson.builder()
            .personalCode(user.getPersonalCode())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .userId(user.getId())
            .build();
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("USER"));
    given(principalService.getFrom(user, Map.of())).willReturn(principal);
    given(grantedAuthorityFactory.from(principal)).willAnswer(invocation -> authorities);
    given(jwtTokenUtil.generateAccessToken(principal, authorities)).willReturn("access-token");

    securityContextRunner.runAs(
        user,
        () -> {
          var authentication = SecurityContextHolder.getContext().getAuthentication();
          assertThat(authentication.getPrincipal()).isEqualTo(principal);
          assertThat(authentication.getCredentials()).isEqualTo("access-token");
        });

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void clearsTheContextWhenTheActionThrows() {
    User user = sampleUser().build();
    AuthenticatedPerson principal =
        AuthenticatedPerson.builder()
            .personalCode(user.getPersonalCode())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .userId(user.getId())
            .build();
    given(principalService.getFrom(user, Map.of())).willReturn(principal);
    given(grantedAuthorityFactory.from(principal)).willAnswer(invocation -> List.of());
    given(jwtTokenUtil.generateAccessToken(principal, List.of())).willReturn("access-token");

    assertThatThrownBy(
            () ->
                securityContextRunner.runAs(
                    user,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
