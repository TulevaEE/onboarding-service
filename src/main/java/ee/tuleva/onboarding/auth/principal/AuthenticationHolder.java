package ee.tuleva.onboarding.auth.principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationHolder {

  public AuthenticatedPerson getAuthenticatedPerson() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedPerson authenticatedPerson) {
      return authenticatedPerson;
    } else {
      throw new RuntimeException("Can not find authentication");
    }
  }
}
