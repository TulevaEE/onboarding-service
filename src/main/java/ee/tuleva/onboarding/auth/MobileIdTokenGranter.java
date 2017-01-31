package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import ee.tuleva.onboarding.user.User;

@Slf4j
public class MobileIdTokenGranter extends AbstractTokenGranter implements TokenGranter {
    public static final String GRANT_TYPE = "mobile_id";

    MobileIdAuthService mobileIdAuthService;

    public MobileIdTokenGranter(AuthorizationServerTokenServices tokenServices,
                                ClientDetailsService clientDetailsService, OAuth2RequestFactory requestFactory) {
        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);
    }

    public void setMobileIdAuthService(MobileIdAuthService mobileIdAuthService) {
        this.mobileIdAuthService = mobileIdAuthService;
    }

    @Override
    protected OAuth2AccessToken getAccessToken(ClientDetails client, TokenRequest tokenRequest) {
        // grant_type validated in AbstractTokenGranter

        assert mobileIdAuthService != null;

        final String clientId = client.getClientId();
        if (clientId == null) {
            throw new InvalidRequestException("Unknown Client ID.");
        }

        MobileIDSession mobileIDSession = MobileIdSessionStore.get();
        boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIDSession);

        if(!isComplete) {
            throw new MobileIdAuthNotCompleteException();
        }

        User user = new User(new Long(12), "isikukood");
        Authentication userAuthentication = new PersonalCodeAuthentication(user, mobileIDSession, null);
        userAuthentication.setAuthenticated(true);

/*

        if (userAuthentication == null) {
            log.error("Failed to authenticate user: got null authentication");
            throw new InvalidGrantException("Invalid user credentials.");
        }
*/

        final OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
        final OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request,
                userAuthentication
        );

        return getTokenServices().createAccessToken(oAuth2Authentication);
    }
}
