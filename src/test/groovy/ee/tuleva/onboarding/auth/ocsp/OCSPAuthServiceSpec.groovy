package ee.tuleva.onboarding.auth.ocsp

import com.codeborne.security.mobileid.CheckCertificateResponse
import ee.tuleva.onboarding.auth.exception.AuthenticationException
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import spock.lang.Specification

class OCSPAuthServiceSpec extends Specification {
    OCSPService service = Mock(OCSPService)
    OCSPUtils utils = Mock(OCSPUtils)
    OCSPAuthService authService = new OCSPAuthService(utils, service)

    def "Test certificate CheckCertificateResponse is valid"() {
        given:
        def validCert = OCSPFixture.generateCertificate("Lepp,Tiit,37801145819", 1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def certificateStr = OCSPFixture.certToString(validCert)
        utils.getX509Certificate(certificateStr) >> validCert
        utils.getIssuerCertificateURI(validCert) >> new URI("http://issuer.ee/ca.crl")
        utils.getResponderURI(validCert) >> new URI("http://issuer.ee/ocsp")
        service.getIssuerCertificate(_) >> "caCert"
        utils.generateOCSPRequest(_, _, _) >> new OCSPRequest("http://issuer.ee/ocsp", validCert, null)
        service.checkCertificate(_) >> OCSPResponseType.GOOD

        def expectedResponse = new CheckCertificateResponse("Tiit", "Lepp", "37801145819")

        when:
        def response = authService.checkCertificate(certificateStr)

        then:
        response.firstName == expectedResponse.firstName
        response.lastName == expectedResponse.lastName
        response.personalCode == expectedResponse.personalCode
    }

    def "Test certificate user credentials invalid"() {
        given:
        def validCert = OCSPFixture.generateCertificate("Lepp,37801145819", 1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def certificateStr = OCSPFixture.certToString(validCert)
        def ocspReq = new OCSPReqBuilder().build()
        utils.getX509Certificate(certificateStr) >> validCert
        utils.getIssuerCertificateURI(validCert) >> new URI("http://issuer.ee/ca.crl")
        utils.getResponderURI(validCert) >> new URI("http://issuer.ee/ocsp")
        service.getIssuerCertificate(_) >> "caCert"

        utils.generateOCSPRequest(_, _, _) >> new OCSPRequest("http://issuer.ee/ocsp", validCert, ocspReq)
        service.checkCertificate(_) >> OCSPResponseType.GOOD

        when:
        authService.checkCertificate(certificateStr)

        then:
        thrown(AuthenticationException)
    }


}
