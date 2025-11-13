package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException
import ee.tuleva.onboarding.auth.ocsp.CheckCertificateResponse
import ee.tuleva.onboarding.auth.ocsp.OCSPAuthService
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification
import spock.lang.Unroll

import java.security.cert.X509Certificate

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.*
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.generateCertificate
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.generateCertificateWithPolicies

class IdCardAuthServiceSpec extends Specification {

    OCSPAuthService authenticator = Mock(OCSPAuthService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    OCSPUtils ocspUtils = Mock(OCSPUtils)
    IdCardAuthService service = new IdCardAuthService(authenticator, sessionStore, ocspUtils)
    static final ID_CARD_EXTENSION = "BEAwPjAyBgsrBgEEAYORIQEBAzAjMCEGCCsGAQUFBwIBFhVodHRwczovL3d3dy5zay5lZS9DUFMwCAYGBACPegEC"

    def "CheckCertificate delegates to the authenticator and saves to session"() {
        given:
        def cert = generateCertificate("Chuck,Norris,38512121212", -1, "SHA1WITHRSA", "https://issuer.ee/ca.crl", "https://issuer.ee/ocsp")
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
        response == DIGITAL_ID_CARD
    }

    def "get document type from certificate throws exception if cert extensions are not what we expect"() {
        given:
        X509Certificate cert = Mock()
        when:
        service.getDocumentTypeFromCertificate(cert)
        then:
        thrown UnknownDocumentTypeException
    }

    @Unroll
    def "parses certificate with OID #documentOid to #expectedType"() {
        given:
        def cert = generateCertificateWithPolicies(documentOid, authPolicyOid)

        when:
        def result = service.getDocumentTypeFromCertificate(cert)

        then:
        result == expectedType

        where:
        documentOid                     | authPolicyOid       | expectedType
        "1.3.6.1.4.1.51361.1.1.1"      | "0.4.0.2042.1.2"   | ESTONIAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.1"      | "0.4.0.2042.1.2"   | ESTONIAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.1.1.2"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.2"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.1.1.3"      | "0.4.0.2042.1.2"   | DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.1.1.4"      | "0.4.0.2042.1.2"   | E_RESIDENT_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.2.1.6"      | "0.4.0.2042.1.2"   | E_RESIDENT_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.1.1.5"      | "0.4.0.2042.1.2"   | LONG_TERM_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.3"      | "0.4.0.2042.1.2"   | LONG_TERM_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.1.1.6"      | "0.4.0.2042.1.2"   | TEMPORARY_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.4"      | "0.4.0.2042.1.2"   | TEMPORARY_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.1.1.7"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.5"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD
        "1.3.6.1.4.1.51455.1.1.1"      | "0.4.0.2042.1.2"   | DIPLOMATIC_ID_CARD
        "1.3.6.1.4.1.51455.2.1.1"      | "0.4.0.2042.1.2"   | DIPLOMATIC_ID_CARD
    }

    @Unroll
    def "rejects certificate with OID #documentOid and policy #authPolicyOid"() {
        given:
        def cert = generateCertificateWithPolicies(documentOid, authPolicyOid)

        when:
        service.getDocumentTypeFromCertificate(cert)

        then:
        thrown UnknownDocumentTypeException

        where:
        documentOid                     | authPolicyOid
        "1.3.6.1.4.1.51361.2.1.1"      | "0.4.0.194112.1.2"
    }

}
