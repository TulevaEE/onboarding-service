package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.AuthenticationHash
import ee.sk.smartid.SmartIdClient
import spock.lang.Specification

import java.util.concurrent.Executor

class SmartIdAuthServiceSpec extends Specification {

    SmartIdAuthService smartIdAuthService
    SmartIdClient smartIdClient = Mock(SmartIdClient)
    SmartIdAuthenticationHashGenerator hashGenerator = Mock(SmartIdAuthenticationHashGenerator)
    Executor smartIdExecutor = Mock(Executor)
    AuthenticationHash hash = Mock(AuthenticationHash)

    def setup() {
        smartIdAuthService = new SmartIdAuthService(smartIdClient, hashGenerator, smartIdExecutor)
    }

    def "StartLogin: Start mobile id login generates hash"() {
        given:
        1 * hash.calculateVerificationCode() >> "12345"
        1 * hashGenerator.generateHash() >> hash
        when:
        SmartIdSession smartIdSession = smartIdAuthService.startLogin(SmartIdFixture.identityCode)
        then:
        smartIdSession.verificationCode == "12345"
    }

    def "StartLogin: Start mobile id login with a phone number"() {
        given:
        1 * hash.calculateVerificationCode() >> "12345"
        1 * hashGenerator.generateHash() >> hash
        when:
        smartIdAuthService.startLogin(SmartIdFixture.identityCode)
        then:
        1 * smartIdExecutor.execute(_)
    }

    def "IsLoginComplete: Login is not complete when no result"() {
        when:
        boolean isLoginComplete = smartIdAuthService.isLoginComplete(SmartIdFixture.sampleSmartIdSession)
        then:
        !isLoginComplete
    }

    def "IsLoginComplete: Login is not complete when result is not valid"() {
        when:
        smartIdAuthService.isLoginComplete(SmartIdFixture.sampleFinalSmartIdSessionWithErrors)
        then:
        thrown(IllegalStateException)
    }

    def "IsLoginComplete: Fetch state of smart id login"() {
        when:
        boolean isLoginComplete = smartIdAuthService.isLoginComplete(SmartIdFixture.sampleFinalSmartIdSession)
        then:
        isLoginComplete
    }
}
