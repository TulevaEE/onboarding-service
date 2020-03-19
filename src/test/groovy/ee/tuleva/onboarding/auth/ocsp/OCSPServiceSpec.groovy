package ee.tuleva.onboarding.auth.ocsp

import ee.tuleva.onboarding.auth.exception.AuthenticationException
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.ocsp.OCSPResponse
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus
import org.bouncycastle.asn1.ocsp.ResponseBytes
import org.bouncycastle.cert.ocsp.OCSPException
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import static org.springframework.http.HttpStatus.OK

class OCSPServiceSpec extends Specification {
    RestTemplate restTemplate = Mock(RestTemplate)
    OCSPService service = new OCSPService(restTemplate)


    def "Test if certificate has expired"() {
        given:
        def ocspGen = new OCSPReqBuilder()
        def expiredCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def ocspRequest = new OCSPRequest(OCSPFixture.sampleExampleServer, expiredCert, ocspGen.build())
        def expectedResponse = OCSPResponseType.EXPIRED

        when:
        def response = service.checkCertificate(ocspRequest)

        then:
        response == expectedResponse
    }

    def "Test get certificate from URL"() {
        given:
        def responseBody = OCSPFixture.sampleCertificateDER.getBytes()
        def expectedResponse = OCSPFixture.getSampleCertificatePEM
        def certUrl = "https://c.sk.ee/EE-GovCA2018.der.crt"
        ResponseEntity<byte[]> result =
            new ResponseEntity(responseBody, OK)

        1 * restTemplate.exchange(certUrl, HttpMethod.GET, _, byte[].class) >> result
        when:
        def response = service.getIssuerCertificate(certUrl)
        then:
        response == expectedResponse
    }

    def "Test resttemplate exception from OCSP response"() {
        given:
        def expiredCert = OCSPFixture.generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp")
        def ocspReq = new OCSPReqBuilder().build()
        def request = new OCSPRequest("http://issuer.ee/ocsp", expiredCert, ocspReq)
        1 * restTemplate.exchange(_, HttpMethod.POST, _, byte[].class) >> { throw new IOException("General Exception") }
        when:
        service.checkCertificateStatus(request)
        then:
        thrown(AuthenticationException)
    }

    def "Test validate malformed OCSP response "() {
        given:
        def basicOCSPResp = new OCSPResp(new OCSPResponse(
            new OCSPResponseStatus(OCSPRespBuilder.MALFORMED_REQUEST), null)).getEncoded()
        def responseStatus = new OCSPResponseStatus(OCSPResponseStatus.MALFORMED_REQUEST)
        def derBasicOCSPResp = new DEROctetString(basicOCSPResp)
        def responseBytes = new ResponseBytes(OCSPObjectIdentifiers.id_pkix_ocsp_basic, derBasicOCSPResp)
        def ocspResponse = new OCSPResponse(responseStatus, responseBytes)
        when:
        service.validateOCSPResponse(new OCSPResp(ocspResponse))
        then:
        thrown(AuthenticationException)
    }

    def "Test validate unauthorized OCSP response "() {
        given:
        def basicOCSPResp = new OCSPResp(new OCSPResponse(
            new OCSPResponseStatus(OCSPRespBuilder.UNAUTHORIZED), null)).getEncoded()
        def responseStatus = new OCSPResponseStatus(OCSPResponseStatus.UNAUTHORIZED)
        def derBasicOCSPResp = new DEROctetString(basicOCSPResp)
        def responseBytes = new ResponseBytes(OCSPObjectIdentifiers.id_pkix_ocsp_basic, derBasicOCSPResp)
        def ocspResponse = new OCSPResponse(responseStatus, responseBytes)
        when:
        service.validateOCSPResponse(new OCSPResp(ocspResponse))
        then:
        thrown(AuthenticationException)
    }

    def "Test validate uncaught OCSP response "() {
        given:
        def basicOCSPResp = new OCSPResp(new OCSPResponse(
            new OCSPResponseStatus(OCSPRespBuilder.INTERNAL_ERROR), null)).getEncoded()
        def responseStatus = new OCSPResponseStatus(OCSPResponseStatus.INTERNAL_ERROR)
        def derBasicOCSPResp = new DEROctetString(basicOCSPResp)
        def responseBytes = new ResponseBytes(OCSPObjectIdentifiers.id_pkix_ocsp_basic, derBasicOCSPResp)
        def ocspResponse = new OCSPResponse(responseStatus, responseBytes)
        when:
        service.validateOCSPResponse(new OCSPResp(ocspResponse))
        then:
        thrown(AuthenticationException)
    }

    def "Test validate OCSPException from OCSP response "() {
        given:
        def basicOCSPResp = new OCSPResp(new OCSPResponse(
            new OCSPResponseStatus(OCSPRespBuilder.SUCCESSFUL), null)).getEncoded()
        def responseStatus = new OCSPResponseStatus(OCSPResponseStatus.SUCCESSFUL)
        def derBasicOCSPResp = new DEROctetString(basicOCSPResp)
        def responseBytes = new ResponseBytes(OCSPObjectIdentifiers.id_pkix_ocsp_basic, derBasicOCSPResp)
        def ocspResponse = new OCSPResponse(responseStatus, responseBytes)
        when:
        service.validateOCSPResponse(new OCSPResp(ocspResponse))
        then:
        thrown(OCSPException)
    }

}
