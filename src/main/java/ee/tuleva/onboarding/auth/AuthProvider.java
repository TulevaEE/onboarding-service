package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;

public interface AuthProvider {
  boolean supports(GrantType grantType);

  AuthenticatedPerson authenticate(String authenticationHash);
}
