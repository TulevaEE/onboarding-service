package ee.tuleva.onboarding.auth.ocsp

import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class OCSPServiceSpec extends Specification {
    RestTemplate template = Mock(RestTemplate);
    OCSPService service = new OCSPService(template);


    def "Test if certificate has expired"() {
        given:
        def ocspGen = new OCSPReqBuilder();
        def expiredCert = OCSPFixture.generateCertificate("CN=Test, L=London, C=GB", -1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", "http://issuer.ee/ocsp");
        def ocspRequest = new OCSPRequest(OCSPFixture.sampleExampleServer, expiredCert, ocspGen.build());
        def expectedResponse = OCSPResponseType.EXPIRED;

        when:
        def response = service.checkCertificate(ocspRequest);

        then:
        response == expectedResponse
    }


}
