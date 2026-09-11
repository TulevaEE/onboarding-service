package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import org.jspecify.annotations.Nullable;

public interface AuthProvider {
  boolean supports(GrantType grantType);

  @Nullable AuthenticatedPerson authenticate(@Nullable String authenticationHash);
}
