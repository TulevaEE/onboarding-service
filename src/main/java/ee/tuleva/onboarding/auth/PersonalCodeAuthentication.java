package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.io.Serializable;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class PersonalCodeAuthentication<C extends Serializable>
    extends AbstractAuthenticationToken {

  private static final long serialVersionUID = -5988919052905713277L;

  private final AuthenticatedPerson principal;
  private final C credentials;

  public PersonalCodeAuthentication(
      AuthenticatedPerson authenticatedPerson,
      C credentials,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = authenticatedPerson;
    this.credentials = credentials;
    setAuthenticated(false);
  }

  @Override
  public C getCredentials() {
    return this.credentials;
  }

  @Override
  public AuthenticatedPerson getPrincipal() {
    return this.principal;
  }
}
