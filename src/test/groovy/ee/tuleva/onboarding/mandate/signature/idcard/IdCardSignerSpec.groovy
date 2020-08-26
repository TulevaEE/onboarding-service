package ee.tuleva.onboarding.mandate.signature.idcard


import ee.tuleva.onboarding.auth.ocsp.OCSPUtils
import ee.tuleva.onboarding.mandate.signature.DigiDocFacade
import ee.tuleva.onboarding.mandate.signature.SignatureFile
import org.apache.commons.codec.binary.Base64
import org.digidoc4j.DataToSign
import org.digidoc4j.impl.asic.asice.AsicEContainer
import spock.lang.Specification
import sun.security.x509.X509CertImpl

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
        def files = [new SignatureFile("fileName", "mimeType", new byte[0])]
        def signingCertificate = "cert"
        def certificate = new X509CertImpl()
        def container = new AsicEContainer()
        def dataToSign = new DataToSign(new byte[0], null)
        def digest = "hello"
        def digestToSign = digest.bytes

        ocspUtils.getX509Certificate(signingCertificate) >> certificate
        digiDocFacade.buildContainer(files) >> container
        digiDocFacade.dataToSign(container, certificate) >> dataToSign
        digiDocFacade.digestToSign(dataToSign) >> digestToSign

        when:
        def signatureSession = idCardSigner.startSign(files, signingCertificate)

        then:
        new String(Base64.decodeBase64(signatureSession.hash)) == digest
        signatureSession.container == container
        signatureSession.dataToSign == dataToSign
    }

    def "can get signed file"() {
        given:
        def session = new IdCardSignatureSession("hash", new DataToSign(new byte[0], null), new AsicEContainer())
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
