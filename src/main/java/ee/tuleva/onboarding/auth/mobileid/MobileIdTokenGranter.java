package ee.tuleva.onboarding.auth.mobileid;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.AuthUserService;
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

import java.util.Optional;

@Slf4j
public class MobileIdTokenGranter extends AbstractTokenGranter implements TokenGranter {
    public static final String GRANT_TYPE = "mobile_id";

    private final MobileIdAuthService mobileIdAuthService;
    private final AuthUserService userService;
    private final GenericSessionStore genericSessionStore;

    public MobileIdTokenGranter(AuthorizationServerTokenServices tokenServices,
                                ClientDetailsService clientDetailsService,
                                OAuth2RequestFactory requestFactory,
                                MobileIdAuthService mobileIdAuthService,
                                AuthUserService userService,
                                GenericSessionStore genericSessionStore) {

        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);

        assert mobileIdAuthService != null;
        assert userService != null;
        assert genericSessionStore != null;

        this.mobileIdAuthService = mobileIdAuthService;
        this.userService = userService;
        this.genericSessionStore = genericSessionStore;
    }

    @Override
    protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
        // grant_type validated in AbstractTokenGranter
        final String clientId = client.getClientId();
        if (clientId == null) {
            log.error("Failed to authenticate client {}", clientId);
            throw new InvalidRequestException("Unknown Client ID.");
        }

        Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
        if (!session.isPresent()) {
            return null;
        }
        MobileIDSession mobileIdSession = session.get();

        boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIdSession);
        if (!isComplete) {
            throw new MobileIdAuthNotCompleteException();
        }

        User user = userService.getByPersonalCode(mobileIdSession.personalCode);

        Authentication userAuthentication = new PersonalCodeAuthentication<>(user, mobileIdSession, null);
        userAuthentication.setAuthenticated(true);

        final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
        final OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request,
                userAuthentication
        );

        return getTokenServices().createAccessToken(oAuth2Authentication);
    }


}
