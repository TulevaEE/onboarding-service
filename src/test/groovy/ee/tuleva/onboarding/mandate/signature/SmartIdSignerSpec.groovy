package ee.tuleva.onboarding.mandate.signature

import com.codeborne.security.mobileid.SignatureFile
import ee.sk.smartid.*
import ee.sk.smartid.rest.SmartIdConnector
import ee.sk.smartid.rest.dao.SessionStatus
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.apache.commons.io.IOUtils
import org.digidoc4j.Container
import org.digidoc4j.DataToSign
import spock.lang.Specification

import java.security.cert.X509Certificate

import static ee.sk.smartid.HashType.SHA256

class SmartIdSignerSpec extends Specification {

    def smartIdClient = Mock(SmartIdClient)
    def connector = Mock(SmartIdConnector)
    def sessionStore = Mock(GenericSessionStore)
    def digiDocFacade = Mock(DigiDocFacade)
    SmartIdSigner signer

    def files = [new SignatureFile("test.txt", "text/plain", "Test".bytes)]
    def personalCode = "38501010002"
    def certificateSessionId = "certificateSessionId"
    def signingSessionId = "signatureSessionId"
    def documentNumber = "PNOEE-372123456"
    def certBuilder = Mock(CertificateRequestBuilder)
    def sigBuilder = Mock(SignatureRequestBuilder)
    def dataToSign = Mock(DataToSign)
    def container = Mock(Container)

    def setup() {
        signer = new SmartIdSigner(smartIdClient, connector, sessionStore, digiDocFacade)

        smartIdClient.getCertificate() >> certBuilder
        certBuilder./withNationalIdentity|withCertificateLevel/(*_) >> certBuilder

        smartIdClient.createSignature() >> sigBuilder
        sigBuilder./withDocumentNumber|withSignableHash|withCertificateLevel/(*_) >> sigBuilder
    }

    def "start signing sets the certificateSessionId in the signature session"() {
        given:
        1 * certBuilder.initiateCertificateChoice() >> certificateSessionId

        when:
        def signatureSession = signer.startSign(files, personalCode)

        then:
        signatureSession.personalCode == personalCode
        signatureSession.files == files
        signatureSession.certificateSessionId == certificateSessionId
    }

    def "does nothing while certificate session is still RUNNING"() {
        given:
        def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
        1 * connector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "RUNNING")

        when:
        def file = signer.getSignedFile(signatureSession)

        then:
        file == null
    }

    def "sets proper signature session variables when cert session is COMPLETE"() {
        given:
        def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
        def certSessionStatus = new SessionStatus(state: "COMPLETE")
        1 * connector.getSessionStatus(certificateSessionId) >> certSessionStatus
        1 * certBuilder.createSmartIdCertificate(certSessionStatus) >> new SmartIdCertificate(
            certificate: Mock(X509Certificate),
            documentNumber: "docNr"
        )
        1 * digiDocFacade.dataToSign(*_) >> dataToSign
        1 * digiDocFacade.buildContainer(files) >> container
        1 * digiDocFacade.digestToSign(_) >> "digest".bytes
        1 * sigBuilder.initiateSigning() >> "signingSessionId"
        when:
        def file = signer.getSignedFile(signatureSession)

        then:
        file == null
        signatureSession.signingSessionId == "signingSessionId"
        signatureSession.challengeCode == "2084"
        signatureSession.documentNumber == "docNr"
        signatureSession.dataToSign == dataToSign
        signatureSession.signableHash.hash == "digest".bytes
        signatureSession.signableHash.hashType == SHA256
        signatureSession.container == container
    }

    def "does nothing while certificate session is COMPLETE and signing session is still RUNNING"() {
        given:
        def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
        signatureSession.signingSessionId = signingSessionId
        1 * connector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "COMPLETE")
        1 * connector.getSessionStatus(signingSessionId) >> new SessionStatus(state: "RUNNING")

        when:
        def file = signer.getSignedFile(signatureSession)

        then:
        file == null
    }

    def "finalizes the signed file when signing session is complete"() {
        given:
        def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
        signatureSession.signingSessionId = signingSessionId
        signatureSession.dataToSign = dataToSign
        signatureSession.container = container
        def completeSession = new SessionStatus(state: "COMPLETE")
        1 * connector.getSessionStatus(certificateSessionId) >> completeSession
        1 * connector.getSessionStatus(signingSessionId) >> completeSession
        1 * sigBuilder.createSmartIdSignature(*_) >> new SmartIdSignature(
            valueInBase64: "IA==",
            algorithmName: "SHA256",
            documentNumber: documentNumber
        )
        container.saveAsStream() >> IOUtils.toInputStream("lol", "UTF-8");

        when:
        def file = signer.getSignedFile(signatureSession)

        then:
        new String(file) == "lol"
    }
}
