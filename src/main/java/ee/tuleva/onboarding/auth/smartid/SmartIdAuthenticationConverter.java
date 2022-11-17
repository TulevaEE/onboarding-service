package ee.tuleva.onboarding.auth.smartid;

import ee.tuleva.onboarding.auth.AuthenticationAttributes;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SmartIdAuthenticationConverter implements AuthenticationConverter {

  private final SmartIdAuthService smartIdAuthService;

  private final PrincipalService principalService;

  private final GrantedAuthorityFactory grantedAuthorityFactory;

  @Override
  public Authentication convert(HttpServletRequest request) {
    String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
    String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

    if (!GrantType.SMART_ID.getValue().equals(grantType)) {
      return null;
    }

    var authenticationHash = request.getParameter("authenticationHash");
    if (authenticationHash == null) {
      throw new SmartIdSessionNotFoundException();
    }

    var identity = smartIdAuthService.getAuthenticationIdentity(authenticationHash);
    if (identity.isEmpty()) {
      throw new AuthNotCompleteException();
    }
    var smartIdPerson = new SmartIdPerson(identity.get());

    AuthenticatedPerson authenticatedPerson =
        principalService.getFrom(smartIdPerson, Optional.empty());

    return new PersonalCodeAuthentication(
        clientId,
        GrantType.SMART_ID,
        authenticatedPerson,
        grantedAuthorityFactory.from(authenticatedPerson),
        new AuthenticationAttributes());
  }
}
