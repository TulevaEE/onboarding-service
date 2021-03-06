package ee.tuleva.onboarding.auth.idcard;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

public class IdCardTokenGranter extends AbstractTokenGranter implements TokenGranter {

  private final GenericSessionStore sessionStore;
  private final PrincipalService principalService;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final ApplicationEventPublisher eventPublisher;

  private static final GrantType GRANT_TYPE = GrantType.ID_CARD;

  public IdCardTokenGranter(
      AuthorizationServerTokenServices tokenServices,
      ClientDetailsService clientDetailsService,
      OAuth2RequestFactory requestFactory,
      GenericSessionStore genericSessionStore,
      PrincipalService principalService,
      GrantedAuthorityFactory grantedAuthorityFactory,
      ApplicationEventPublisher applicationEventPublisher) {
    super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE.name().toLowerCase());
    this.sessionStore = genericSessionStore;
    this.principalService = principalService;
    this.grantedAuthorityFactory = grantedAuthorityFactory;
    this.eventPublisher = applicationEventPublisher;
  }

  @Override
  protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
    final String clientId = client.getClientId();
    if (clientId == null) {
      throw new InvalidRequestException("Unknown Client ID.");
    }

    Optional<IdCardSession> session = sessionStore.get(IdCardSession.class);
    if (!session.isPresent()) {
      throw new IdCardSessionNotFoundException();
    }
    IdCardSession idCardSession = session.get();

    AuthenticatedPerson authenticatedPerson = principalService.getFrom(idCardSession);

    Authentication userAuthentication =
        new PersonalCodeAuthentication<>(
            authenticatedPerson, idCardSession, grantedAuthorityFactory.from(authenticatedPerson));
    userAuthentication.setAuthenticated(true);

    OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
    OAuth2Authentication oAuth2Authentication =
        new OAuth2Authentication(oAuth2Request, userAuthentication);

    eventPublisher.publishEvent(
        new BeforeTokenGrantedEvent(this, authenticatedPerson, oAuth2Authentication, GRANT_TYPE));

    OAuth2AccessToken accessToken = getTokenServices().createAccessToken(oAuth2Authentication);

    eventPublisher.publishEvent(new AfterTokenGrantedEvent(this, authenticatedPerson, accessToken));

    return accessToken;
  }
}
