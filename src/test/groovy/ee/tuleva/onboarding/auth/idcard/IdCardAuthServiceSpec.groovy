package ee.tuleva.onboarding.auth.idcard

import com.codeborne.security.mobileid.CheckCertificateResponse
import com.codeborne.security.mobileid.MobileIDAuthenticator
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

class IdCardAuthServiceSpec extends Specification {

    MobileIDAuthenticator authenticator = Mock(MobileIDAuthenticator)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    IdCardAuthService service = new IdCardAuthService(authenticator, sessionStore)

    def "CheckCertificate delegates to the authenticator and saves to session"() {
        given:
        authenticator.checkCertificate("cert") >> new CheckCertificateResponse("Chuck", "Norris", "38512121212")
        def expectedResponse = new IdCardSession("Chuck", "Norris", "38512121212")

        when:
        def response = service.checkCertificate("cert")

        then:
        1 * sessionStore.save(expectedResponse)
        response == expectedResponse
    }

}
