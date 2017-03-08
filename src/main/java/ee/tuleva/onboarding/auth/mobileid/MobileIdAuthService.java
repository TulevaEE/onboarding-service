package ee.tuleva.onboarding.auth.mobileid;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIDSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class MobileIdAuthService {

    private final MobileIDAuthenticator authenticator;

    public MobileIDSession startLogin(String phoneNumber) {
        MobileIDSession mobileIDSession = authenticator.startLogin(phoneNumber);
        log.info("Mobile ID authentication with challenge " + mobileIDSession.challenge + " sent to " + phoneNumber);
        return mobileIDSession;
    }

    public boolean isLoginComplete(MobileIDSession mobileIDSession) {
        return authenticator.isLoginComplete(mobileIDSession);
    }
}