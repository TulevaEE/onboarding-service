package ee.tuleva.onboarding.signature.idcard

import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.signature.DigiDocFacade
import ee.tuleva.onboarding.signature.IdCardSignatureSession
import ee.tuleva.onboarding.signature.SignatureFile
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

        1 * ocspUtils.decodeX09Certificate(signingCertificate) >> certificate
        1 * digiDocFacade.buildContainer(files) >> container
        1 * digiDocFacade.dataToSign(container, certificate) >> dataToSign
        1 * digiDocFacade.digestToSign(dataToSign) >> digestToSign

        when:
        def signatureSession = idCardSigner.startSign(files, signingCertificate)

        then:
        new String(Hex.decode(signatureSession.hashToSignInHex)) == hashToSign
        signatureSession.container == container
        signatureSession.dataToSign == dataToSign
    }

    def "can get signed file"() {
        given:
        def dataToSign = Mock(DataToSign)
        def container = Mock(Container)
        def session = new IdCardSignatureSession("68656c6c6f", dataToSign, container)
        def signedHash = "68656c6c6f"
        def signature = Hex.decode(signedHash)
        def signedContainer = "signed".bytes

        1 * digiDocFacade.addSignatureToContainer(signature, dataToSign, container) >> signedContainer

        when:
        def signedFile = idCardSigner.getSignedFile(session, signedHash)

        then:
        signedFile == signedContainer
    }
}
