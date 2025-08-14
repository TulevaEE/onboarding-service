package ee.tuleva.onboarding.mandate.signature.idcard

import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.signature.DigiDocFacade
import ee.tuleva.onboarding.signature.SignatureFile
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession
import ee.tuleva.onboarding.signature.idcard.IdCardSigner
import org.bouncycastle.util.encoders.Hex
import org.digidoc4j.Container
import org.digidoc4j.DataToSign
import spock.lang.Specification

import java.security.cert.X509Certificate

class IdCardSignerSpec extends Specification {

    OCSPUtils ocspUtils
    DigiDocFacade digiDocFacade
    IdCardSigner idCardSigner

    def setup() {
        ocspUtils = Mock(OCSPUtils)
        digiDocFacade = Mock(DigiDocFacade)
        idCardSigner = new IdCardSigner(ocspUtils, digiDocFacade)
    }

    def "can start id card signature"() {
        given:
        def files = [new SignatureFile("fileName", "mimeType", "content".bytes)]
        def signingCertificate = "cert"
        def certificate = Mock(X509Certificate)
        def container = Mock(Container)
        def dataToSign = Mock(DataToSign)
        def hashToSign = "hello"
        def digestToSign = hashToSign.bytes

        ocspUtils.decodeX09Certificate(signingCertificate) >> certificate
        digiDocFacade.buildContainer(files) >> container
        digiDocFacade.dataToSign(container, certificate) >> dataToSign
        digiDocFacade.digestToSign(dataToSign) >> digestToSign

        when:
        def signatureSession = idCardSigner.startSign(files, signingCertificate)

        then:
        new String(Hex.decode(signatureSession.hashToSignInHex)) == hashToSign
        signatureSession.container == container
        signatureSession.dataToSign == dataToSign
    }

    def "can get signed file"() {
        given:
        def session = new IdCardSignatureSession("hash", Mock(DataToSign), Mock(Container))
        def signedHash = ""
        def signature = new byte[0]
        def signedContainer = new byte[0]

        digiDocFacade.addSignatureToContainer(signature, session.getDataToSign(), session.getContainer()) >> signedContainer

        when:
        def signedFile = idCardSigner.getSignedFile(session, signedHash)

        then:
        signedFile == signedContainer
    }
}
