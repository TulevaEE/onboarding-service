package ee.tuleva.onboarding.auth.manager;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.io.Serializable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshingAuthenticationManager implements AuthenticationManager {

  private final GrantedAuthorityFactory grantedAuthorityFactory;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {

    PersonalCodeAuthentication<?> personalCodeAuthentication =
        (PersonalCodeAuthentication<?>)
            requireNonNull(authentication.getPrincipal(), "Principal missing");
    AuthenticatedPerson authenticatedPerson = personalCodeAuthentication.getPrincipal();

    List<? extends GrantedAuthority> updatedAuthorities =
        grantedAuthorityFactory.from(authenticatedPerson);

    return withRefreshedAuthorities(personalCodeAuthentication, updatedAuthorities);
  }

  private <C extends Serializable> Authentication withRefreshedAuthorities(
      PersonalCodeAuthentication<C> personalCodeAuthentication,
      List<? extends GrantedAuthority> updatedAuthorities) {
    Authentication newUserAuth =
        new PersonalCodeAuthentication<>(
            personalCodeAuthentication.getPrincipal(),
            personalCodeAuthentication.getCredentials(),
            updatedAuthorities);

    newUserAuth.setAuthenticated(true);

    return newUserAuth;
  }
}
