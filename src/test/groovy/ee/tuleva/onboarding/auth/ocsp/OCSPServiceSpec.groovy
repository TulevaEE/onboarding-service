package ee.tuleva.onboarding.auth.ocsp


import ee.tuleva.onboarding.time.ClockConfig
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.ocsp.OCSPResponse
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus
import org.bouncycastle.asn1.ocsp.ResponseBytes
import org.bouncycastle.cert.ocsp.OCSPException
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.nio.file.Files

import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.generateCertificate
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.sampleExampleServer
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.GOOD
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.REVOKED
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(OCSPService)
@ContextConfiguration(classes = [ClockConfig.class, OCSPService.class])
class OCSPServiceSpec extends Specification {
    @Autowired
    private MockRestServiceServer server
    @Autowired
    private OCSPService service

    def "Test if certificate has expired"() {
        given:
        def ocspGen = new OCSPReqBuilder()
        def expiredCert = generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", sampleExampleServer)
        def ocspRequest = new OCSPRequest(sampleExampleServer, expiredCert, ocspGen.build())
        when:
        def response = service.checkCertificate(ocspRequest)

        then:
        response == OCSPResponseType.EXPIRED
    }

    def "Test get certificate from URL"() {
        given:
        def responseBody = readFile("sampleCert.der.crt")
        def expectedResponse = readFile("sampleCert.pem.crt")

        server.expect(requestTo(sampleExampleServer))
            .andRespond(withSuccess(responseBody,
                MediaType.APPLICATION_OCTET_STREAM))
        when:
        def response = service.getIssuerCertificate(sampleExampleServer)
        then:
        response == expectedResponse
    }

    def "Test is valid certificate"() {
        given:
        def responseBody = readBytes("validCertificate")
        def ocspGen = new OCSPReqBuilder()
        def validCert = generateCertificate("Tiit,Lepp,37801145819", 10, "SHA1WITHRSA", "http://issuer.ee/ca.crl", sampleExampleServer)
        def ocspRequest = new OCSPRequest(sampleExampleServer, validCert, ocspGen.build())

        server.expect(requestTo(sampleExampleServer))
            .andRespond(withSuccess(responseBody,
                MediaType.APPLICATION_OCTET_STREAM))
        when:
        def response = service.checkCertificate(ocspRequest)
        then:
        response == GOOD
    }

    def "Test is revoked certificate"() {
        given:
        def responseBody = readBytes("revokedCertificate")
        def ocspGen = new OCSPReqBuilder()
        def revokedCert = generateCertificate("Tiit,Lepp,37801145819", 10, "SHA1WITHRSA", "http://issuer.ee/ca.crl", sampleExampleServer)
        def ocspRequest = new OCSPRequest(sampleExampleServer, revokedCert, ocspGen.build())

        server.expect(requestTo(sampleExampleServer))
            .andRespond(withSuccess(responseBody,
                MediaType.APPLICATION_OCTET_STREAM))
        when:
        def response = service.checkCertificate(ocspRequest)
        then:
        response == REVOKED
    }

    def "Test resttemplate exception from OCSP response"() {
        given:
        def expiredCert = generateCertificate("Tiit,Lepp,37801145819", -1, "SHA1WITHRSA", "https://c.sk.ee/EE-GovCA2018.der.crt", sampleExampleServer)
        def ocspReq = new OCSPReqBuilder().build()
        def request = new OCSPRequest(sampleExampleServer, expiredCert, ocspReq)
        server.expect(requestTo(sampleExampleServer))
            .andRespond(withServerError())
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

    private String readFile(String fileName) {
        def resource = new ClassPathResource(fileName)
        new String(Files.readAllBytes(resource.getFile().toPath()))
    }

    private byte[] readBytes(String fileName) {
        def resource = new ClassPathResource(fileName)
        return Files.readAllBytes(resource.getFile().toPath())
    }


}

