package ee.tuleva.onboarding.auth

import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.MobileIDSession
import spock.lang.Specification

class MobileIdAuthServiceSpec extends Specification {

    MobileIdAuthService mobileIdAuthService
    String samplePhoneNumber = "5532533";
    MobileIDSession sampleMobileIdSession = new MobileIDSession(
            123, "challenge", "firstName", "lastName", "personalCode"
    );
    MobileIDAuthenticator mobileIDAuthenticator = Mock(MobileIDAuthenticator)

    def setup() {
        mobileIdAuthService = new MobileIdAuthService();
        mobileIdAuthService.setMobileIdAuthenticator(mobileIDAuthenticator)
    }

    def "StartLogin: Start mobile id login with a phone number"() {
        given:
        1 * mobileIDAuthenticator.startLogin(_ as String) >> sampleMobileIdSession
        when:
        MobileIDSession mobileIDSession = mobileIdAuthService.startLogin(samplePhoneNumber)
        then:
        mobileIDSession == sampleMobileIdSession
    }

    def "IsLoginComplete: Fetch state of mobile id login"() {
        given:
        1 * mobileIDAuthenticator.isLoginComplete(sampleMobileIdSession) >> true
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        isLoginComplete == true
    }
}
