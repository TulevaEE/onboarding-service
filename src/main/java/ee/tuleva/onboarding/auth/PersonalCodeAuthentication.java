package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

public class PersonalCodeAuthentication extends AbstractAuthenticationToken
    implements OAuth2AuthenticatedPrincipal {

  private static final long serialVersionUID = -5988919052905713277L;

  private final AuthenticatedPerson principal;

  @Getter private final AuthenticationAttributes authenticationAttributes;

  @Getter private final String clientId;

  @Getter private final AuthorizationGrantType authorizationGrantType;

  public PersonalCodeAuthentication(
      String clientId,
      AuthorizationGrantType authorizationGrantType,
      AuthenticatedPerson authenticatedPerson,
      Collection<? extends GrantedAuthority> authorities,
      AuthenticationAttributes attributes) {
    super(authorities);
    this.clientId = clientId;
    this.authorizationGrantType = authorizationGrantType;
    this.principal = authenticatedPerson;
    this.authenticationAttributes = attributes;
    setAuthenticated(false);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public AuthenticatedPerson getPrincipal() {
    return this.principal;
  }

  public Map<String, Object> getAttributes() {
    return authenticationAttributes.toMap();
  }
}
