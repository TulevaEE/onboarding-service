package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

@Slf4j
public class MobileIdTokenGranter extends AbstractTokenGranter implements TokenGranter {
    public static final String GRANT_TYPE = "mobile_id";

    private final MobileIdAuthService mobileIdAuthService;
    private final UserRepository userRepository;
    private final MobileIdSessionStore mobileIdSessionStore;

    public MobileIdTokenGranter(AuthorizationServerTokenServices tokenServices,
                                ClientDetailsService clientDetailsService,
                                OAuth2RequestFactory requestFactory,
                                MobileIdAuthService mobileIdAuthService,
                                UserRepository userRepository,
                                MobileIdSessionStore mobileIdSessionStore) {

        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);

        assert mobileIdAuthService != null;
        assert userRepository != null;
        assert mobileIdSessionStore != null;

        this.mobileIdAuthService = mobileIdAuthService;
        this.userRepository = userRepository;
        this.mobileIdSessionStore = mobileIdSessionStore;
    }

    @Override
    protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
        // grant_type validated in AbstractTokenGranter
        final String clientId = client.getClientId();
        if (clientId == null) {
            log.error("Failed to authenticate client {}", clientId);
            throw new InvalidRequestException("Unknown Client ID.");
        }

        MobileIDSession mobileIDSession = mobileIdSessionStore.get();
        boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIDSession);

        if(!isComplete) {
            throw new MobileIdAuthNotCompleteException();
        }

        User user = userRepository.findByPersonalCode(mobileIDSession.personalCode);

        if (user == null) {
            log.error("Failed to authenticate user: couldn't find user with personal code {}", mobileIDSession.personalCode);
            throw new InvalidRequestException("INVALID_USER_CREDENTIALS");
        }

        if (user.getActive() ==  false) {
            log.info("Failed to login inactive user with personal code {}", mobileIDSession.personalCode);
            throw new InvalidRequestException("INACTIVE_USER");
        }

        Authentication userAuthentication = new PersonalCodeAuthentication(user, mobileIDSession, null);
        userAuthentication.setAuthenticated(true);

        final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
        final OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request,
                userAuthentication
        );

        return getTokenServices().createAccessToken(oAuth2Authentication);
    }
}
