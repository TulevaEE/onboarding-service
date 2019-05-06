package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.*
import ee.sk.smartid.exception.TechnicalErrorException
import ee.sk.smartid.rest.dao.NationalIdentity
import spock.lang.Specification

import java.util.concurrent.Executor

class SmartIdAuthServiceSpec extends Specification {

    SmartIdAuthService smartIdAuthService
    SmartIdClient smartIdClient = Mock(SmartIdClient)
    SmartIdAuthenticationHashGenerator hashGenerator = Mock(SmartIdAuthenticationHashGenerator)
    Executor smartIdExecutor = Mock(Executor)
    AuthenticationHash hash = Mock(AuthenticationHash)
    AuthenticationResponseValidator validator = new AuthenticationResponseValidator()

    def setup() {
        smartIdAuthService = new SmartIdAuthService(smartIdClient, hashGenerator, smartIdExecutor, validator)
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

    def "GetSmartIdAuthenticationResponse: calls authenticate"() {
        given:
        NationalIdentity identity = new NationalIdentity("EE", SmartIdFixture.identityCode)
        AuthenticationRequestBuilder mockBuilder = Mock(AuthenticationRequestBuilder)
        1 * smartIdClient.createAuthentication() >> mockBuilder
        1 * mockBuilder.withNationalIdentity(identity) >> mockBuilder
        1 * mockBuilder.withAuthenticationHash(hash) >> mockBuilder
        1 * mockBuilder.withCertificateLevel("QUALIFIED") >> mockBuilder
        when:
        smartIdAuthService.getSmartIdAuthenticationResponse(identity, hash)
        then:
        1 * mockBuilder.authenticate()
    }

    def "GetAuthenticationResult: throws when no cert"() {
        given:
        SmartIdAuthenticationResponse response = Mock(SmartIdAuthenticationResponse)
        when:
        smartIdAuthService.getAuthenticationResult(response)
        then:
        thrown(TechnicalErrorException)
    }
}
