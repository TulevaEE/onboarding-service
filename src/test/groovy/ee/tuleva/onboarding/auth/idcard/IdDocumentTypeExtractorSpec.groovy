package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException
import ee.tuleva.onboarding.auth.idcard.normalizer.CertificateNormalizer
import ee.tuleva.onboarding.auth.idcard.normalizer.ProductionCertificateNormalizer
import spock.lang.Specification

import javax.security.auth.x500.X500Principal
import java.security.cert.X509Certificate

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.*
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.*

class IdDocumentTypeExtractorSpec extends Specification {

    CertificateNormalizer normalizer = new ProductionCertificateNormalizer()
    IdDocumentTypeExtractor extractor = new IdDocumentTypeExtractor([], normalizer)
    static final DIGITAL_ID_CARD_EXTENSION_BASE64 = "BEAwPjAyBgsrBgEEAYORIQEBAzAjMCEGCCsGAQUFBwIBFhVodHRwczovL3d3dy5zay5lZS9DUFMwCAYGBACPegEC"

    def "extract document type from certificate"() {
        given:
        X509Certificate cert = Mock()
        cert.getExtensionValue("2.5.29.32") >> Base64.getDecoder().decode(DIGITAL_ID_CARD_EXTENSION_BASE64)

        when:
        def response = extractor.extract(cert)

        then:
        response == DIGITAL_ID_CARD
    }

    def "extract throws exception if cert extensions are not what we expect"() {
        given:
        X509Certificate cert = Mock()

        when:
        extractor.extract(cert)

        then:
        thrown UnknownDocumentTypeException
    }

    def "parses certificate with OID #documentOid to #expectedType"() {
        given:
        def cert = generateCertificateWithPolicies(documentOid, authPolicyOid)

        when:
        def result = extractor.extract(cert)

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

    def "parses 2025 certificate with reversed policy order OID #documentOid to #expectedType"() {
        given:
        def cert = generate2025CertificateWithPolicies(documentOid, authPolicyOid)

        when:
        def result = extractor.extract(cert)

        then:
        result == expectedType

        where:
        documentOid                     | authPolicyOid       | expectedType
        "1.3.6.1.4.1.51361.2.1.1"      | "0.4.0.2042.1.2"   | ESTONIAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.2"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.3"      | "0.4.0.2042.1.2"   | LONG_TERM_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.4"      | "0.4.0.2042.1.2"   | TEMPORARY_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.5"      | "0.4.0.2042.1.2"   | EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.6"      | "0.4.0.2042.1.2"   | E_RESIDENT_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51455.2.1.1"      | "0.4.0.2042.1.2"   | DIPLOMATIC_ID_CARD
    }

    def "rejects certificate with OID #documentOid and policy #authPolicyOid"() {
        given:
        def cert = generateCertificateWithPolicies(documentOid, authPolicyOid)

        when:
        extractor.extract(cert)

        then:
        thrown UnknownDocumentTypeException

        where:
        documentOid                     | authPolicyOid
        "1.3.6.1.4.1.51361.2.1.1"      | "0.4.0.194112.1.2"
    }

    def "rejects 2025 certificate with missing auth policy"() {
        given:
        def cert = generate2025CertificateWithPolicies("1.3.6.1.4.1.51361.2.1.1", "0.4.0.194112.1.2")

        when:
        extractor.extract(cert)

        then:
        thrown UnknownDocumentTypeException
    }

    def "checkClientAuthentication passes for valid certificate"() {
        given:
        X509Certificate cert = Mock()
        cert.getExtensionValue("2.5.29.37") >> buildExtendedKeyUsageExtension("1.3.6.1.5.5.7.3.2")

        when:
        extractor.checkClientAuthentication(cert)

        then:
        noExceptionThrown()
    }

    def "checkClientAuthentication throws exception for invalid certificate"() {
        given:
        X509Certificate cert = Mock()
        cert.getExtensionValue("2.5.29.37") >> null

        when:
        extractor.checkClientAuthentication(cert)

        then:
        thrown UnknownExtendedKeyUsageException
    }

    def "checkIssuer passes for valid issuer ESTEID-SK 2015"() {
        given:
        X509Certificate cert = Mock()
        cert.getIssuerX500Principal() >> new X500Principal(
            "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE")

        when:
        extractor.checkIssuer(cert)

        then:
        noExceptionThrown()
    }

    def "checkIssuer passes for valid issuer ESTEID2018"() {
        given:
        X509Certificate cert = Mock()
        cert.getIssuerX500Principal() >> new X500Principal(
            "CN=ESTEID2018, OID.2.5.4.97=NTREE-10747013, O=SK ID Solutions AS, C=EE")

        when:
        extractor.checkIssuer(cert)

        then:
        noExceptionThrown()
    }

    def "checkIssuer passes for valid issuer ESTEID2025"() {
        given:
        X509Certificate cert = Mock()
        cert.getIssuerX500Principal() >> new X500Principal(
            "C=EE, O=Zetes Estonia OÜ, OID.2.5.4.97=NTREE-17066049, CN=ESTEID2025")

        when:
        extractor.checkIssuer(cert)

        then:
        noExceptionThrown()
    }

    def "checkIssuer throws exception for unknown issuer"() {
        given:
        X509Certificate cert = Mock()
        cert.getIssuerX500Principal() >> new X500Principal(
            "CN=UNKNOWN, O=Unknown CA, C=XX")

        when:
        extractor.checkIssuer(cert)

        then:
        thrown UnknownIssuerException
    }
}
