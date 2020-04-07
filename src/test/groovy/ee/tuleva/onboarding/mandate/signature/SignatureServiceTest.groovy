package ee.tuleva.onboarding.mandate.signature

import com.codeborne.security.mobileid.IdCardSignatureSession
import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.SignatureFile
import ee.sk.mid.MidClient
import ee.sk.mid.MidSignature
import ee.sk.mid.rest.MidConnector
import ee.sk.mid.rest.MidSessionStatusPoller
import ee.sk.mid.rest.dao.MidSessionStatus
import ee.sk.mid.rest.dao.response.MidSignatureResponse
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdCertificateService
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureService
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession
import org.digidoc4j.Configuration
import org.digidoc4j.DataToSign
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.generateCertificate
import static ee.tuleva.onboarding.auth.ocsp.OCSPFixture.sampleExampleServer
import static java.util.Collections.singletonList

class SignatureServiceTest extends Specification {

    def signer = Mock(MobileIDAuthenticator)
    def smartIdSigner = Mock(SmartIdSigner)
    def service = new SignatureService(signer, smartIdSigner)
    def certificateService = Mock(MobileIdCertificateService)
    def connector = Mock(MidConnector)
    def configuration = Mock(Configuration)
    def client = Mock(MidClient)
    def poller = Mock(MidSessionStatusPoller)
    def mobileIdSignatureService = new MobileIdSignatureService(certificateService, connector, configuration, client, poller)

    def "startSign() works for mobile id"() {
        given:
        List<SignatureFile> files = Arrays.asList(
            new SignatureFile("test1.txt", "text/plain", "Test1".bytes),
            new SignatureFile("test2.txt", "text/plain", "Test2".bytes)
        )

        certificateService.getCertificate(_, _) >> generateCertificate("Tiit,Lepp,37801145819", 1, "SHA1WITHRSA", "http://issuer.ee/ca.crl", sampleExampleServer)
        connector.sign(_) >> new MidSignatureResponse("1")
        when:
        MobileIdSignatureSession session = mobileIdSignatureService.startSign(files as List<SignatureFile>, "38501010002", "55555555")

        then:
        session.verificationCode.length() == 4
        session.sessionID == "1"
    }

    def "getSignedFile() works for mobile id"() {
        given:

        def session = MobileIdSignatureSession.newBuilder().withSessionID("1").withDataToSign(new DataToSign("Test".getBytes(), null, null)).build()
        // mobileIdSignatureService.getSignedFile(session) >> ([0] as byte[])
        def midSignature = new MidSignature.MobileIdSignatureBuilder().withValueInBase64("1234").build()
        poller.fetchFinalSignatureSessionStatus("1") >> new MidSessionStatus()
        client.createMobileIdSignature(_) >> midSignature
        when:
        def fileContent = mobileIdSignatureService.getSignedFile(session)

        then:
        fileContent == [0] as byte[]
    }

    def "startSign() works with id card"() {
        given:
        def expectedSession = new IdCardSignatureSession(1, "sigId", "hash")
        def files = singletonList(new SignatureFile("file.txt", "text/plain", new byte[1]))
        signer.startSign(files, "signCert") >> expectedSession

        when:
        def session = service.startSign(files, "signCert")

        then:
        session == expectedSession
    }

    def "getSignedFile() works with id card"() {
        given:
        def session = new IdCardSignatureSession(1, "sigId", "hash")
        def expectedFile = new byte[1]
        signer.getSignedFile(session, "signedHash") >> expectedFile

        when:
        def file = service.getSignedFile(session, "signedHash")

        then:
        file == expectedFile
    }
}
