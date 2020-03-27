package ee.tuleva.onboarding.auth.mobileid

import ee.sk.mid.MidAuthentication
import ee.sk.mid.MidAuthenticationResponseValidator
import ee.sk.mid.MidClient
import ee.sk.mid.exception.*
import ee.sk.mid.rest.MidConnector
import ee.sk.mid.rest.MidSessionStatusPoller
import ee.sk.mid.rest.dao.MidSessionStatus
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse
import ee.tuleva.onboarding.auth.exception.MidOperationException
import spock.lang.Specification

class MobileIdAuthServiceSpec extends Specification {

    MobileIdAuthService mobileIdAuthService
    MidClient client = Mock(MidClient)
    MidAuthenticationResponseValidator validator = Mock(MidAuthenticationResponseValidator)

    def setup() {
        mobileIdAuthService = new MobileIdAuthService(client, validator)
    }

    def "StartLogin: Start mobile id login with a phone number"() {
        given:
        1 * client.getMobileIdConnector() >> Mock(MidConnector, {
            authenticate(_) >> new MidAuthenticationResponse(MobileIdFixture.sampleSessionId)
        })

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber, MobileIdFixture.sampleIdCode)
        then:
        mobileIDSession.getSessionId() == MobileIdFixture.sampleSessionId
        mobileIDSession.getPhoneNumber() == MobileIdFixture.sampleLongPhoneNumber
    }

    def "StartLogin: Start mobile id login with a country prefix number"() {
        given:
        1 * client.getMobileIdConnector() >> Mock(MidConnector, {
            authenticate(_) >> new MidAuthenticationResponse(MobileIdFixture.sampleSessionId)
        })

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(MobileIdFixture.sampleLongPhoneNumber, MobileIdFixture.sampleIdCode)
        then:
        mobileIDSession.getSessionId() == MobileIdFixture.sampleSessionId
        mobileIDSession.getPhoneNumber() == MobileIdFixture.sampleLongPhoneNumber
    }

    def "StartLogin: Start mobile id login with a lithuanian number"() {
        given:
        1 * client.getMobileIdConnector() >> Mock(MidConnector, {
            authenticate(_) >> new MidAuthenticationResponse(MobileIdFixture.sampleSessionId)
        })

        when:
        def mobileIDSession = mobileIdAuthService.startLogin(MobileIdFixture.sampleLithuanianPhoneNumber, MobileIdFixture.sampleIdCode)
        then:
        mobileIDSession.getSessionId() == MobileIdFixture.sampleSessionId
        mobileIDSession.getPhoneNumber() == MobileIdFixture.sampleLongLithuanianPhoneNumber
    }

    def "IsLoginComplete: Fetch state of mobile id login"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> MobileIdFixture.getSampleMidAuthResult(true)
        when:
        boolean isLoginComplete = mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        isLoginComplete == true
    }

    def "IsLoginComplete: Fetch invalid state of mobile id login"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> MobileIdFixture.getSampleMidAuthResult(false)
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: User has cancelled login operation"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidUserCancellationException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: User is not a MID client"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidNotMidClientException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: User did not type in PIN code before session timeout"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidSessionTimeoutException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: Unable to reach phone/SIM card"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidPhoneNotAvailableException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: Error communicating with the phone/SIM card"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidDeliveryException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: Mobile-ID configuration invalid"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidInvalidUserConfigurationException() }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: Integrator-side error with MID integration "() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidMissingOrInvalidParameterException("Invalid parameter") }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }

    def "IsLoginComplete: MID service returned internal error"() {
        given:
        1 * client.getSessionStatusPoller() >> Mock(MidSessionStatusPoller, {
            fetchFinalAuthenticationSessionStatus(_) >> new MidSessionStatus()
        })
        1 * client.createMobileIdAuthentication(_, _) >> new MidAuthentication.MobileIdAuthenticationBuilder().withResult("OK").withSignatureValueInBase64("bGVhc3VyZS4=").build()
        1 * validator.validate(_) >> { throw new MidInternalErrorException("Internal Error") }
        when:
        mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession)
        then:
        thrown(MidOperationException)
    }
}
