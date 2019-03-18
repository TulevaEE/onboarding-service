package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.tuleva.onboarding.auth.BeforeTokenGrantedEventPublisher;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
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
    private static final String GRANT_TYPE = "smart_id";

    private final SmartIdAuthService smartIdAuthService;
    private final PrincipalService principalService;
    private final GenericSessionStore genericSessionStore;
    private final GrantedAuthorityFactory grantedAuthorityFactory;
    private BeforeTokenGrantedEventPublisher beforeTokenGrantedEventPublisher;

    public SmartIdTokenGranter(AuthorizationServerTokenServices tokenServices,
                               ClientDetailsService clientDetailsService,
                               OAuth2RequestFactory requestFactory,
                               SmartIdAuthService smartIdAuthService,
                               PrincipalService principalService,
                               GenericSessionStore genericSessionStore,
                               GrantedAuthorityFactory grantedAuthorityFactory,
                               ApplicationEventPublisher applicationEventPublisher) {
        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);

        assert smartIdAuthService != null;
        assert principalService != null;
        assert genericSessionStore != null;
        assert grantedAuthorityFactory != null;

        this.smartIdAuthService = smartIdAuthService;
        this.principalService = principalService;
        this.genericSessionStore = genericSessionStore;
        this.grantedAuthorityFactory = grantedAuthorityFactory;
        this.beforeTokenGrantedEventPublisher = new BeforeTokenGrantedEventPublisher(applicationEventPublisher);
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
            return null;
        }
        SmartIdSession smartIdSession = session.get();

        boolean isComplete = smartIdAuthService.isLoginComplete(smartIdSession);
        if (!isComplete) {
            throw new AuthNotCompleteException();
        }

        AuthenticationIdentity identity = smartIdSession.authenticationResult.getAuthenticationIdentity();

        AuthenticatedPerson authenticatedPerson = principalService.getFrom(new Person() {
            @Override
            public String getPersonalCode() {
                return identity.getIdentityCode();
            }

            @Override
            public String getFirstName() {
                return identity.getGivenName();
            }

            @Override
            public String getLastName() {
                return identity.getSurName();
            }
        });

        Authentication userAuthentication =
                new PersonalCodeAuthentication<>(
                        authenticatedPerson,
                        smartIdSession,
                        grantedAuthorityFactory.from(authenticatedPerson)
                );

        userAuthentication.setAuthenticated(true);

        final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
        final OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request,
                userAuthentication
        );

        beforeTokenGrantedEventPublisher.publish(oAuth2Authentication);

        return getTokenServices().createAccessToken(oAuth2Authentication);
    }
}
