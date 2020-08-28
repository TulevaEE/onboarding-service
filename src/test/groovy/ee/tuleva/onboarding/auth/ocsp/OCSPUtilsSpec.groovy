package ee.tuleva.onboarding.auth.ocsp

import org.apache.commons.codec.binary.Hex
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class OCSPUtilsSpec extends Specification {
    OCSPUtils ocspUtils = new OCSPUtils()

    def "Test if Issuer Certificate URI is correct"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def expectedResponse = new URI("http://issuer.ee/ca.crl")

        when:
        def response = ocspUtils.getIssuerCertificateURI(cert)

        then:
        response == expectedResponse
    }

    def "Test error when Issuer Certificate URI is missing"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", null, "http://issuer.ee/ocsp")

        when:
        ocspUtils.getIssuerCertificateURI(cert)

        then:
        thrown(AuthenticationException)
    }

    def "Test if Responder URI is correct"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def expectedResponse = new URI("http://issuer.ee/ocsp")

        when:
        def response = ocspUtils.getResponderURI(cert)

        then:
        response == expectedResponse
    }

    def "Test error when Responder URI is missing"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", null)

        when:
        ocspUtils.getResponderURI(cert)

        then:
        thrown(AuthenticationException)
    }

    def "Test error when both URIs are missing (getResponderURI)"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", null, null)

        when:
        ocspUtils.getResponderURI(cert)

        then:
        thrown(AuthenticationException)
    }

    def "Test error when both URIs are missing (getIssuerCertificateURI)"() {
        given:
        def cert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", null, null)

        when:
        ocspUtils.getIssuerCertificateURI(cert)

        then:
        thrown(AuthenticationException)
    }


    def "Test if X509Certificate is generated from PEM"() {
        given:
        def originalCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def certString = OCSPFixture.certToString(originalCert)

        when:
        def response = ocspUtils.getX509Certificate(certString)

        then:
        response == originalCert
    }

    def "Test if X509Certificate is properly decoded from hex"() {
        given:
        def originalCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def certString = OCSPFixture.certToString(originalCert)
        def certificateInHex = Hex.encodeHexString(certString.bytes)

        when:
        def response = ocspUtils.decodeX09Certificate(certificateInHex)

        then:
        response == originalCert
    }

    def "Test if malformed X509Certificate generates exception"() {
        given:

        def originalCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def certString = OCSPFixture.certToString(originalCert)
        certString = new String(certString.getBytes(StandardCharsets.US_ASCII), StandardCharsets.UTF_16)
        when:
        ocspUtils.getX509Certificate(certString)

        then:
        thrown(AuthenticationException)
    }

    def "Test if OCSPRequest is generated"() {
        given:
        def caCert = OCSPFixture.generateCertificate("CertAuth", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def selfCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def caCertString = OCSPFixture.certToString(caCert)
        when:
        def response = ocspUtils.generateOCSPRequest(selfCert, caCertString, "http://issuer.ee/ocsp")
        then:
        response.getCertificate() == selfCert
        response.getOcspServer() == "http://issuer.ee/ocsp"
    }

    def "Test if certificate malformed upon OCSPRequest and generates exception"() {
        given:
        def caCert = OCSPFixture.generateCertificate("CertAuth", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def selfCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def caCertString = OCSPFixture.certToString(caCert)
        caCertString = new String(caCertString.getBytes(StandardCharsets.US_ASCII), StandardCharsets.UTF_16)
        when:
        ocspUtils.generateOCSPRequest(selfCert, caCertString, "http://issuer.ee/ocsp")
        then:
        thrown(AuthenticationException)
    }

    def "Test if certificate malformed upon OCSPReq and generates exception"() {
        given:
        def caCert = OCSPFixture.generateCertificate("CertAuth", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def selfCert = Mock(X509Certificate)
        selfCert.getSerialNumber() >> { throw new CertificateException("General Exception") }
        def caCertString = OCSPFixture.certToString(caCert)
        when:
        ocspUtils.generateOCSPRequest(selfCert, caCertString, "http://issuer.ee/ocsp")
        then:
        thrown(AuthenticationException)
    }

}
