package ee.tuleva.onboarding.auth.smartid;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

import java.util.Optional;

@Slf4j
public class SmartIdTokenGranter extends AbstractTokenGranter {
  private static final GrantType GRANT_TYPE = GrantType.SMART_ID;

  private final SmartIdAuthService smartIdAuthService;
  private final PrincipalService principalService;
  private final GenericSessionStore genericSessionStore;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final ApplicationEventPublisher eventPublisher;

  public SmartIdTokenGranter(
      AuthorizationServerTokenServices tokenServices,
      ClientDetailsService clientDetailsService,
      OAuth2RequestFactory requestFactory,
      SmartIdAuthService smartIdAuthService,
      PrincipalService principalService,
      GenericSessionStore genericSessionStore,
      GrantedAuthorityFactory grantedAuthorityFactory,
      ApplicationEventPublisher applicationEventPublisher) {
    super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE.name().toLowerCase());

    assert smartIdAuthService != null;
    assert principalService != null;
    assert genericSessionStore != null;
    assert grantedAuthorityFactory != null;

    this.smartIdAuthService = smartIdAuthService;
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

    Optional<SmartIdSession> session = genericSessionStore.get(SmartIdSession.class);
    if (!session.isPresent()) {
      throw new SmartIdSessionNotFoundException();
    }
    SmartIdSession smartIdSession = session.get();

    boolean isComplete = smartIdAuthService.isLoginComplete(smartIdSession);
    if (!isComplete) {
      throw new AuthNotCompleteException();
    }

    AuthenticatedPerson authenticatedPerson = principalService.getFrom(smartIdSession);

    Authentication userAuthentication =
        new PersonalCodeAuthentication<>(
            authenticatedPerson, smartIdSession, grantedAuthorityFactory.from(authenticatedPerson));

    userAuthentication.setAuthenticated(true);

    final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
    final OAuth2Authentication oAuth2Authentication =
        new OAuth2Authentication(oAuth2Request, userAuthentication);

    eventPublisher.publishEvent(new BeforeTokenGrantedEvent(this, authenticatedPerson, oAuth2Authentication, GRANT_TYPE));

    OAuth2AccessToken accessToken = getTokenServices().createAccessToken(oAuth2Authentication);

    eventPublisher.publishEvent(new AfterTokenGrantedEvent(this, authenticatedPerson, accessToken));

    return accessToken;
  }
}
