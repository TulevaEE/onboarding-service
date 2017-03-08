package ee.tuleva.onboarding.auth.mobileid

import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.MobileIDSession
import spock.lang.Specification

class MobileIdAuthServiceSpec extends Specification {

    MobileIdAuthService mobileIdAuthService
    MobileIDAuthenticator mobileIDAuthenticator = Mock(MobileIDAuthenticator)

    def setup() {
        mobileIdAuthService = new MobileIdAuthService(mobileIDAuthenticator)
    }

    def "StartLogin: Start mobile id login with a phone number"() {
        given:
        1 * mobileIDAuthenticator.startLogin(_ as String) >> MobileIdFixture.sampleMobileIdSession
        when:
        MobileIDSession mobileIDSession = mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber)
        then:
        mobileIDSession == MobileIdFixture.sampleMobileIdSession
    }

    def "IsLoginComplete: Fetch state of mobile id login"() {
        given:
        1 * mobileIDAuthenticator.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        isLoginComplete == true
    }
}
