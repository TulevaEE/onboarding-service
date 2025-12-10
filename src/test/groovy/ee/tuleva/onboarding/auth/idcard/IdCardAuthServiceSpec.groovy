package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.ocsp.CheckCertificateResponse
import ee.tuleva.onboarding.auth.ocsp.OCSPAuthService
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.DIPLOMATIC_ID_CARD
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.generateCertificate

class IdCardAuthServiceSpec extends Specification {

    OCSPAuthService authenticator = Mock(OCSPAuthService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    OCSPUtils ocspUtils = Mock(OCSPUtils)
    IdDocumentTypeExtractor documentTypeExtractor = Mock(IdDocumentTypeExtractor)
    IdCardAuthService service = new IdCardAuthService(authenticator, sessionStore, ocspUtils, documentTypeExtractor)

    def "CheckCertificate delegates to the authenticator and saves to session"() {
        given:
        def cert = generateCertificate("Chuck,Norris,38512121212", -1, "SHA1WITHRSA", "https://issuer.ee/ca.crl", "https://issuer.ee/ocsp")
        ocspUtils.getX509Certificate("cert") >> cert
        authenticator.checkCertificate(cert) >> new CheckCertificateResponse("Chuck", "Norris", "38512121212")
        documentTypeExtractor.extract(cert) >> DIPLOMATIC_ID_CARD
        def expectedResponse = new IdCardSession("Chuck", "Norris", "38512121212", DIPLOMATIC_ID_CARD)

        when:
        def response = service.checkCertificate("cert")

        then:
        1 * sessionStore.save(expectedResponse)
        1 * documentTypeExtractor.checkClientAuthentication(cert)
        1 * documentTypeExtractor.checkIssuer(cert)
        response == expectedResponse
    }
}
