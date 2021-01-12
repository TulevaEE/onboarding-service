package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException
import ee.tuleva.onboarding.auth.ocsp.CheckCertificateResponse
import ee.tuleva.onboarding.auth.ocsp.OCSPAuthService
import ee.tuleva.onboarding.auth.ocsp.OCSPFixture
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import java.security.cert.X509Certificate

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.DIPLOMATIC_ID_CARD

class IdCardAuthServiceSpec extends Specification {

    OCSPAuthService authenticator = Mock(OCSPAuthService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    OCSPUtils ocspUtils = Mock(OCSPUtils)
    IdCardAuthService service = new IdCardAuthService(authenticator, sessionStore, ocspUtils)
    static final ID_CARD_EXTENSION = "BEAwPjAyBgsrBgEEAYORIQEBAzAjMCEGCCsGAQUFBwIBFhVodHRwczovL3d3dy5zay5lZS9DUFMwCAYGBACPegEC"

    def "CheckCertificate delegates to the authenticator and saves to session"() {
        given:
        def cert = OCSPFixture.generateCertificate("Chuck,Norris,38512121212", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        ocspUtils.getX509Certificate("cert") >> cert
        authenticator.checkCertificate(cert) >> new CheckCertificateResponse("Chuck", "Norris", "38512121212")
        def expectedResponse = new IdCardSession("Chuck", "Norris", "38512121212", DIPLOMATIC_ID_CARD)

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

    def "get document type from certificate throws exception if cert extensions are not what we expect"() {
        given:
        X509Certificate cert = Mock()
        when:
        service.getDocumentTypeFromCertificate(cert)
        then:
        thrown UnknownDocumentTypeException
    }

}
