package ee.tuleva.onboarding.auth;

import com.codeborne.security.AuthenticationException;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIDSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class MobileIdAuthService {

    private static final String TEST_DIGIDOC_SERVICE_URL = "https://tsp.demo.sk.ee/";
    private static final String KEYSTORE_PATH = "test_keys/keystore.jks";
    private MobileIDAuthenticator mid = new MobileIDAuthenticator(TEST_DIGIDOC_SERVICE_URL);

    public MobileIdAuthService() {
        System.setProperty("javax.net.ssl.trustStore", KEYSTORE_PATH);
    }

    public MobileIDSession fullLogin(String phoneNumber) {
        MobileIDSession mobileIDSession;
        try {
            mobileIDSession = startLogin(phoneNumber);
            waitForLogin(mobileIDSession);
        } catch (AuthenticationException e) {
            e.printStackTrace();
            log.info("Mobile ID authentication failed" + e.getMessage());
            return null;
        }

        return mobileIDSession;
    }

    public MobileIDSession startLogin(String phoneNumber) {
        MobileIDSession mobileIDSession = mid.startLogin(phoneNumber);
        log.info("Mobile ID authentication with challenge " + mobileIDSession.challenge + " sent to " + phoneNumber);
        return mobileIDSession;
    }

    public MobileIDSession waitForLogin(MobileIDSession mobileIDSession) {
        log.info("Waiting for mobile ID login: " + mobileIDSession.sessCode);
        mid.waitForLogin(mobileIDSession);
        log.info("Mobile ID authentication success! First name: " + mobileIDSession.firstName + ", Last name: " + mobileIDSession.lastName + ", Personal code: " + mobileIDSession.personalCode);
        return mobileIDSession;
    }

    public boolean isLoginComplete(MobileIDSession mobileIDSession) {
        return mid.isLoginComplete(mobileIDSession);
    }
}