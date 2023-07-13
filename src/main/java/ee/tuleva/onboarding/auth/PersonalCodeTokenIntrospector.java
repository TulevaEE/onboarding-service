package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

@RequiredArgsConstructor
public class PersonalCodeTokenIntrospector implements OpaqueTokenIntrospector {

  private final OAuth2AuthorizationService oAuth2AuthorizationService;

  private final PrincipalService principalService;

  private final RegisteredClientRepository registeredClientRepository;

  private final GrantedAuthorityFactory grantedAuthorityFactory;

  @Override
  public OAuth2AuthenticatedPrincipal introspect(String token) {
    var authorization = oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);

    if (authorization == null) {
      throw new BadOpaqueTokenException("Authorization not found");
    }

    if (authorization.getAuthorizationGrantType().getValue().equals(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())) {
      var authorities = AuthorityUtils.createAuthorityList("SERVICE");
      return new OAuth2IntrospectionAuthenticatedPrincipal(authorization.getAttributes(), authorities);
    }

    AuthenticatedPerson authenticatedPerson =
        principalService.getFrom(authorization.getPrincipalName());

    List<? extends GrantedAuthority> updatedAuthorities =
        grantedAuthorityFactory.from(authenticatedPerson);

    var registeredClient =
        registeredClientRepository.findById(authorization.getRegisteredClientId());

    Map<String, Object> authenticationAttributes =
        authorization.getAttribute(AuthenticationAttributes.AUTHENTICATION_ATTRIBUTES_KEY);

    var attributes = new AuthenticationAttributes(authenticationAttributes);

    PersonalCodeAuthentication personalCodeAuthentication =
        new PersonalCodeAuthentication(
            registeredClient.getClientId(),
            authorization.getAuthorizationGrantType(),
            authenticatedPerson,
            updatedAuthorities,
            attributes);

    personalCodeAuthentication.setAuthenticated(true);

    var accessToken = authorization.getAccessToken().getToken();

    personalCodeAuthentication
        .getAuthenticationAttributes()
        .setIssueTime(accessToken.getIssuedAt());
    personalCodeAuthentication
        .getAuthenticationAttributes()
        .setExpirationTime(accessToken.getExpiresAt());

    return personalCodeAuthentication;
  }
}
