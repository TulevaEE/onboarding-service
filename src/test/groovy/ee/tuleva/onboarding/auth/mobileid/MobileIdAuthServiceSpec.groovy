package ee.tuleva.onboarding.auth.mobileid

import ee.sk.mid.MidAuthentication
import ee.sk.mid.MidAuthenticationResponseValidator
import ee.sk.mid.MidClient
import ee.sk.mid.exception.*
import ee.sk.mid.rest.MidConnector
import ee.sk.mid.rest.MidSessionStatusPoller
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.*

class MobileIdAuthServiceSpec extends Specification {

    MobileIdAuthService mobileIdAuthService
    MidClient client = Mock(MidClient)
    MidAuthenticationResponseValidator validator = Mock(MidAuthenticationResponseValidator)
    MidConnector connector = Mock(MidConnector)
    MidSessionStatusPoller poller = Mock(MidSessionStatusPoller)

    def setup() {
        mobileIdAuthService = new MobileIdAuthService(client, validator, connector, poller)
    }

    def "StartLogin: Start mobile id login with a phone number"() {
        given:
        1 * connector.authenticate(_) >> new MidAuthenticationResponse(sampleSessionId)

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(samplePhoneNumber, sampleIdCode)
        then:
        mobileIDSession.getSessionId() == sampleSessionId
        mobileIDSession.getPhoneNumber() == sampleLongPhoneNumber
    }

    def "StartLogin: Start mobile id login with a country prefix number"() {
        given:
        1 * connector.authenticate(_) >> new MidAuthenticationResponse(sampleSessionId)

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(sampleLongPhoneNumber, sampleIdCode)
        then:
        mobileIDSession.getSessionId() == sampleSessionId
        mobileIDSession.getPhoneNumber() == sampleLongPhoneNumber
    }

    def "StartLogin: Start mobile id login with a lithuanian number"() {
        given:
        1 * connector.authenticate(_) >> new MidAuthenticationResponse(sampleSessionId)

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(sampleLithuanianPhoneNumber, sampleIdCode)
        then:
        mobileIDSession.getSessionId() == sampleSessionId
        mobileIDSession.getPhoneNumber() == sampleLongLithuanianPhoneNumber
    }

    def "IsLoginComplete: Fetch state of mobile id login"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> getSampleMidSessionComplete()
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> getSampleMidAuthResult(true)
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        isLoginComplete == true
    }

    def "IsLoginComplete: Fetch invalid state of mobile id login"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> getSampleMidSessionComplete()
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> getSampleMidAuthResult(false)
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: Mobile ID authentication response is still in RUNNING status"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> getSampleMidSessionIncomplete()
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        isLoginComplete == false
    }

    def "IsLoginComplete: Mobile ID sessionStatus is missing"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> null
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        isLoginComplete == false
    }


    def "IsLoginComplete: Mobile ID authentication response has some other response status"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> getSampleMidSessionOther()
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        isLoginComplete == false
    }

    def "IsLoginComplete: User has cancelled login operation"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidUserCancellationException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: User is not a MID client"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidNotMidClientException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: User did not type in PIN code before session timeout"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidSessionTimeoutException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: Unable to reach phone/SIM card"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidPhoneNotAvailableException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: Error communicating with the phone/SIM card"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidDeliveryException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: Mobile-ID configuration invalid"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidInvalidUserConfigurationException() }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: Integrator-side error with MID integration "() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidMissingOrInvalidParameterException("Invalid parameter") }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }

    def "IsLoginComplete: MID service returned internal error"() {
        given:
        1 * poller.fetchFinalAuthenticationSessionStatus(_) >> { throw new MidInternalErrorException("Internal Error") }
        when:
        mobileIdAuthService.isLoginComplete(sampleMobileIdSession)
        then:
        thrown(MobileIdException)
    }
}
