package ee.tuleva.onboarding.auth.manager;

import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthManager implements AuthenticationManager {

  private final GrantedAuthorityFactory grantedAuthorityFactory;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {

    PersonalCodeAuthentication personalCodeAuthentication = (PersonalCodeAuthentication) authentication.getPrincipal();
    AuthenticatedPerson authenticatedPerson = personalCodeAuthentication.getPrincipal();

    List<? extends GrantedAuthority> updatedAuthorities = grantedAuthorityFactory.from(authenticatedPerson);

    Authentication newUserAuth = new PersonalCodeAuthentication<>(
      authenticatedPerson,
      personalCodeAuthentication.getCredentials(),
      updatedAuthorities);

    newUserAuth.setAuthenticated(true);

    return newUserAuth;
  }

}
