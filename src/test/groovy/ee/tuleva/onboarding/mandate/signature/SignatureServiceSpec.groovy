package ee.tuleva.onboarding.mandate.signature

import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSigner
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSigner
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSigner
import spock.lang.Specification

class SignatureServiceSpec extends Specification {

    def smartIdSigner = Mock(SmartIdSigner)
    def mobileIdSigner = Mock(MobileIdSigner)
    def idCardSigner = Mock(IdCardSigner)
    def service = new SignatureService(smartIdSigner, mobileIdSigner, idCardSigner)

    def "startSign() works for mobile id"() {
        given:
        def files = [new SignatureFile("test.txt", "text/plain", "fileContent".bytes)]
        def signingSession = Mock(MobileIdSignatureSession)

        1 * mobileIdSigner.startSign(files, "38501010002", "55555555") >> signingSession

        when:
        MobileIdSignatureSession session = service.startMobileIdSign(files, "38501010002", "55555555")

        then:
        session == signingSession
    }

    def "getSignedFile() works for mobile id"() {
        given:
        def session = Mock(MobileIdSignatureSession)
        def content = "fileContent".bytes
        mobileIdSigner.getSignedFile(session) >> content

        when:
        def fileContent = service.getSignedFile(session)

        then:
        fileContent == content
    }

    def "startSign() works with id card"() {
        given:
        def signatureSession = Mock(IdCardSignatureSession)
        def files = [new SignatureFile("file.txt", "text/plain", "fileContent".bytes)]
        def signingCertificate = "signingCertificate"
        idCardSigner.startSign(files, signingCertificate) >> signatureSession

        when:
        def session = service.startIdCardSign(files, signingCertificate)

        then:
        session == signatureSession
    }

    def "getSignedFile() works with id card"() {
        given:
        def session = Mock(IdCardSignatureSession)
        def file = "fileContent".bytes
        def signedHashInHex = "signedHashInHex"
        idCardSigner.getSignedFile(session, signedHashInHex) >> file

        when:
        def signedFile = service.getSignedFile(session, signedHashInHex)

        then:
        signedFile == file
    }

    def "startSign() works with smart id"() {
        given:
        def signatureSession = Mock(SmartIdSignatureSession)
        def files = [new SignatureFile("file.txt", "text/plain", "fileContent".bytes)]
        def personalCode = "38501010002"
        smartIdSigner.startSign(files, personalCode) >> signatureSession

        when:
        def session = service.startSmartIdSign(files, personalCode)

        then:
        session == signatureSession
    }

    def "getSignedFile() works with smart id"() {
        given:
        def signatureSession = Mock(SmartIdSignatureSession)
        def file = "fileContent".bytes
        smartIdSigner.getSignedFile(signatureSession) >> file

        when:
        def signedFile = service.getSignedFile(signatureSession)

        then:
        signedFile == file
    }
}
