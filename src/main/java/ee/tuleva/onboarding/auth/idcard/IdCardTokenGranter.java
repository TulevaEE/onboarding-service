package ee.tuleva.onboarding.auth.idcard;

import ee.tuleva.onboarding.auth.AuthUserService;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

import java.util.Optional;

public class IdCardTokenGranter extends AbstractTokenGranter implements TokenGranter {

    private final IdCardSessionStore idCardSessionStore;
    private final AuthUserService authUserService;

    private static final String GRANT_TYPE = "id_card";

    public IdCardTokenGranter(AuthorizationServerTokenServices tokenServices,
                                 ClientDetailsService clientDetailsService,
                                 OAuth2RequestFactory requestFactory,
                                 IdCardSessionStore idCardSessionStore,
                                 AuthUserService authUserService) {
        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);
        this.idCardSessionStore = idCardSessionStore;
        this.authUserService = authUserService;
    }

    @Override
    protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
        final String clientId = client.getClientId();
        if (clientId == null) {
            throw new InvalidRequestException("Unknown Client ID.");
        }

        Optional<IdCardSession> session = idCardSessionStore.get();
        if (!session.isPresent()) {
            return null;
        }
        IdCardSession idCardSession = session.get();

        User user = authUserService.getByPersonalCode(idCardSession.personalCode);

        Authentication userAuthentication = new PersonalCodeAuthentication<>(user, idCardSession, null);
        userAuthentication.setAuthenticated(true);

        OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
        OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request, userAuthentication);

        return getTokenServices().createAccessToken(oAuth2Authentication);
    }
}
