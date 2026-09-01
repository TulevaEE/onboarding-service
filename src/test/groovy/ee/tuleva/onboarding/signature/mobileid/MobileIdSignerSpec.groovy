package ee.tuleva.onboarding.signature.mobileid

import ee.sk.mid.MidClient
import ee.sk.mid.MidSignature
import ee.sk.mid.exception.MidInternalErrorException
import ee.sk.mid.rest.MidConnector
import ee.sk.mid.rest.dao.MidSessionStatus
import ee.sk.mid.rest.dao.request.MidCertificateRequest
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse
import ee.sk.mid.rest.dao.response.MidSignatureResponse
import ee.tuleva.onboarding.signature.DigiDocFacade
import ee.tuleva.onboarding.signature.MobileIdSignatureSession
import ee.tuleva.onboarding.signature.SignatureFile
import org.digidoc4j.Container
import org.digidoc4j.DataToSign
import spock.lang.Specification

import java.security.cert.X509Certificate

class MobileIdSignerSpec extends Specification {

    MidClient mobileIdClient
    MidConnector mobileIdConnector
    DigiDocFacade digiDocFacade
    MobileIdSigner mobileIdSigner

    def personalCode = "38888888888"
    def phoneNumber = "+37255555555"

    def setup() {
        mobileIdClient = Mock(MidClient)
        mobileIdConnector = Mock(MidConnector)
        digiDocFacade = Mock(DigiDocFacade)
        mobileIdSigner = new MobileIdSigner(mobileIdClient, mobileIdConnector, digiDocFacade)
    }

    def "can start signing with mobile id"() {
        given:
        def files = [new SignatureFile("test.txt", "text/plain", "content".bytes)]
        def container = Mock(Container)
        def dataToSign = Mock(DataToSign)
        def certificate = Mock(X509Certificate)
        def sessionId = "sessionId123"
        def certificateResponse = new MidCertificateChoiceResponse()

        1 * mobileIdConnector.getCertificate({ MidCertificateRequest request ->
            request.phoneNumber == phoneNumber && request.nationalIdentityNumber == personalCode
        }) >> certificateResponse
        1 * mobileIdClient.createMobileIdCertificate(certificateResponse) >> certificate
        1 * digiDocFacade.buildContainer(files) >> container
        1 * digiDocFacade.dataToSign(container, certificate) >> dataToSign
        1 * dataToSign.getDataToSign() >> "dataToSign".bytes
        1 * mobileIdConnector.sign({ it.phoneNumber == phoneNumber && it.nationalIdentityNumber == personalCode }) >> new MidSignatureResponse(sessionId)

        when:
        def signatureSession = mobileIdSigner.startSign(files, personalCode, phoneNumber)

        then:
        signatureSession.sessionId == sessionId
        signatureSession.dataToSign == dataToSign
        signatureSession.container == container
        signatureSession.verificationCode == "5072"
    }

    def "can get signed file"() {
        given:
        def signatureSession = new MobileIdSignatureSession("sessionId", "1234", Mock(DataToSign), Mock(Container))
        def sessionStatus = new MidSessionStatus(state: "COMPLETE")
        def signature = MidSignature.newBuilder()
            .withValueInBase64("IA==")
            .withAlgorithmName("SHA256")
            .build()
        def signedContainer = new byte[0]

        1 * mobileIdConnector.getSessionStatus(*_) >> sessionStatus
        1 * mobileIdClient.createMobileIdSignature(sessionStatus) >> signature
        1 * digiDocFacade.addSignatureToContainer(
            signature.value, signatureSession.dataToSign, signatureSession.container) >> signedContainer

        when:
        def signedFile = mobileIdSigner.getSignedFile(signatureSession)

        then:
        signedFile == signedContainer
    }

    def "returns null while session status is not yet available"() {
        given:
        def signatureSession = Mock(MobileIdSignatureSession)

        1 * mobileIdConnector.getSessionStatus(*_) >> null

        when:
        def signedFile = mobileIdSigner.getSignedFile(signatureSession)

        then:
        signedFile == null
    }

    def "does nothing while signing session is still RUNNING"() {
        given:
        def signatureSession = Mock(MobileIdSignatureSession)
        def sessionStatus = new MidSessionStatus(state: "RUNNING")

        1 * mobileIdConnector.getSessionStatus(*_) >> sessionStatus

        when:
        def signedFile = mobileIdSigner.getSignedFile(signatureSession)

        then:
        signedFile == null
    }

    def "throws when the session status is neither RUNNING nor COMPLETE"() {
        given:
        def signatureSession = Mock(MobileIdSignatureSession)
        def sessionStatus = new MidSessionStatus(state: "EXPIRED_TRANSACTION")

        1 * mobileIdConnector.getSessionStatus(*_) >> sessionStatus

        when:
        mobileIdSigner.getSignedFile(signatureSession)

        then:
        thrown(MidInternalErrorException)
    }
}
