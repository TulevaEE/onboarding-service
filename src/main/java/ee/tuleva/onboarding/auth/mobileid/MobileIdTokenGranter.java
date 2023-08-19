package ee.tuleva.onboarding.auth.mobileid;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.OAuth2RequestFactory;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

@Slf4j
public class MobileIdTokenGranter extends AbstractTokenGranter implements TokenGranter {
  private static final GrantType GRANT_TYPE = GrantType.MOBILE_ID;

  private final MobileIdAuthService mobileIdAuthService;
  private final PrincipalService principalService;
  private final GenericSessionStore genericSessionStore;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final ApplicationEventPublisher eventPublisher;

  private final JwtTokenUtil jwtTokenUtil;

  public MobileIdTokenGranter(
      AuthorizationServerTokenServices tokenServices,
      ClientDetailsService clientDetailsService,
      OAuth2RequestFactory requestFactory,
      MobileIdAuthService mobileIdAuthService,
      PrincipalService principalService,
      GenericSessionStore genericSessionStore,
      GrantedAuthorityFactory grantedAuthorityFactory,
      ApplicationEventPublisher applicationEventPublisher, JwtTokenUtil jwtTokenUtil) {

    super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE.name().toLowerCase());
    this.jwtTokenUtil = jwtTokenUtil;

    assert mobileIdAuthService != null;
    assert principalService != null;
    assert genericSessionStore != null;
    assert grantedAuthorityFactory != null;

    this.mobileIdAuthService = mobileIdAuthService;
    this.principalService = principalService;
    this.genericSessionStore = genericSessionStore;
    this.grantedAuthorityFactory = grantedAuthorityFactory;
    this.eventPublisher = applicationEventPublisher;
  }

  @Override
  protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
    // grant_type validated in AbstractTokenGranter
    final String clientId = client.getClientId();
    if (clientId == null) {
      log.error("Failed to authenticate client");
      throw new InvalidRequestException("Unknown Client ID.");
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
        principalService.getFrom(mobileIdSession, Optional.of(mobileIdSession.getPhoneNumber()));

    final var authorities = grantedAuthorityFactory.from(authenticatedPerson);

    Authentication userAuthentication =
        new PersonalCodeAuthentication<>(
            authenticatedPerson,
            mobileIdSession,
            authorities);

    userAuthentication.setAuthenticated(true);

    final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
    final OAuth2Authentication oAuth2Authentication =
        new OAuth2Authentication(oAuth2Request, userAuthentication);

    eventPublisher.publishEvent(
        new BeforeTokenGrantedEvent(this, authenticatedPerson, oAuth2Authentication, GRANT_TYPE));

    OAuth2AccessToken accessToken = getTokenServices().createAccessToken(oAuth2Authentication);

    String jwtToken = jwtTokenUtil.generateToken(authenticatedPerson, authorities);

    eventPublisher.publishEvent(new AfterTokenGrantedEvent(this, authenticatedPerson, accessToken, jwtToken));

    return accessToken;
  }
}
