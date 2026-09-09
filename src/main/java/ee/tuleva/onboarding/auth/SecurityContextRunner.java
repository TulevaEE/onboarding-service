package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextRunner {

  private final PrincipalService principalService;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final JwtTokenUtil jwtTokenUtil;

  public void runAs(Person person, Runnable action) {
    try {
      setupSecurityContext(person);
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  public <T> T callAs(Person person, Supplier<T> action) {
    Authentication previous = SecurityContextHolder.getContext().getAuthentication();
    try {
      setupSecurityContext(person);
      return action.get();
    } finally {
      SecurityContextHolder.clearContext();
      if (previous != null) {
        SecurityContextHolder.getContext().setAuthentication(previous);
      }
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

  private void setupSecurityContext(Person person) {
    final var principal = principalService.getFrom(person, Map.of());
    final var authorities = grantedAuthorityFactory.from(principal);
    final var accessToken = jwtTokenUtil.generateAccessToken(principal, authorities);

    final var authenticationToken =
        new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);

    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
