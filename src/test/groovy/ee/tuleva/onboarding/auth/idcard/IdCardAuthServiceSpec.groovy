package ee.tuleva.onboarding.auth.idcard

import com.codeborne.security.mobileid.CheckCertificateResponse
import com.codeborne.security.mobileid.MobileIDAuthenticator
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import java.security.cert.X509Certificate

class IdCardAuthServiceSpec extends Specification {

    MobileIDAuthenticator authenticator = Mock(MobileIDAuthenticator)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    IdCardAuthService service = new IdCardAuthService(authenticator, sessionStore)
    static final ID_CARD_EXTENSION = "BEAwPjAyBgsrBgEEAYORIQEBAzAjMCEGCCsGAQUFBwIBFhVodHRwczovL3d3dy5zay5lZS9DUFMwCAYGBACPegEC"

    def "CheckCertificate delegates to the authenticator and saves to session"() {
        given:
        authenticator.checkCertificate("cert") >> new CheckCertificateResponse("Chuck", "Norris", "38512121212")
        def expectedResponse = new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.UNKNOWN)

        when:
        def response = service.checkCertificate("cert")

        then:
        1 * sessionStore.save(expectedResponse)
        response == expectedResponse
    }

    def "get document type from certificate"() {
        given:
        X509Certificate cert = Mock()
        cert.getExtensionValue("2.5.29.32") >> Base64.getDecoder().decode(ID_CARD_EXTENSION)
        when:
        def response = service.getDocumentTypeFromCertificate(cert)
        then:
        response == IdDocumentType.DIGITAL_ID_CARD
    }

    def "get document type from certificate return unknown if cert extensions are not what we expect"() {
        given:
        X509Certificate cert = Mock()
        when:
        def response = service.getDocumentTypeFromCertificate(cert)
        then:
        response == IdDocumentType.UNKNOWN
    }

}
