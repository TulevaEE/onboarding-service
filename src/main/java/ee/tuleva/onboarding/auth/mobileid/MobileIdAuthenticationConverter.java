package ee.tuleva.onboarding.auth.mobileid;

import ee.tuleva.onboarding.auth.AuthenticationAttributes;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MobileIdAuthenticationConverter implements AuthenticationConverter {

  private final PrincipalService principalService;

  private final GrantedAuthorityFactory grantedAuthorityFactory;

  private final GenericSessionStore genericSessionStore;

  private final MobileIdAuthService mobileIdAuthService;

  @Override
  public Authentication convert(HttpServletRequest request) {
    String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
    String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

    if (!GrantType.MOBILE_ID.getValue().equals(grantType)) {
      return null;
    }

    if (clientId == null) {
      log.error("Failed to authenticate client");
      throw new OAuth2AuthenticationException("Unknown Client ID.");
    }

    Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
    if (!session.isPresent()) {
      throw new MobileIdSessionNotFoundException();
    }
    MobileIDSession mobileIdSession = session.get();

    boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIdSession);
    if (!isComplete) {
      throw new AuthNotCompleteException();
    }

    AuthenticatedPerson authenticatedPerson =
        principalService.getFrom(
            mobileIdSession, Optional.ofNullable(mobileIdSession.getPhoneNumber()));

    return new PersonalCodeAuthentication(
        clientId,
        GrantType.MOBILE_ID,
        authenticatedPerson,
        grantedAuthorityFactory.from(authenticatedPerson),
        new AuthenticationAttributes());
  }
}
