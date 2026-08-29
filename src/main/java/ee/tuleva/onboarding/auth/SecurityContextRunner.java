package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextRunner {

  private final PrincipalService principalService;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final JwtTokenUtil jwtTokenUtil;

  public void runAs(User user, Runnable action) {
    try {
      setupSecurityContext(user);
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  public void runAs(AuthenticatedPerson person, String accessToken, Runnable action) {
    try {
      final var authorities = grantedAuthorityFactory.from(person);
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(person, accessToken, authorities));
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void setupSecurityContext(User user) {
    final var principal = principalService.getFrom(user, Map.of());
    final var authorities = grantedAuthorityFactory.from(principal);
    final var accessToken = jwtTokenUtil.generateAccessToken(principal, authorities);

    final var authenticationToken =
        new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);

    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
