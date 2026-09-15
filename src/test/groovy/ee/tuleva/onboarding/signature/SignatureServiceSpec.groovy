package ee.tuleva.onboarding.signature

import ee.tuleva.onboarding.signature.idcard.IdCardSigner
import ee.tuleva.onboarding.signature.mobileid.MobileIdSigner
import ee.tuleva.onboarding.signature.smartid.SmartIdSigner
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class SignatureServiceSpec extends Specification {

    def smartIdSigner = Mock(SmartIdSigner)
    def mobileIdSigner = Mock(MobileIdSigner)
    def idCardSigner = Mock(IdCardSigner)
    def service = new SignatureService(smartIdSigner, mobileIdSigner, idCardSigner)

    def personalCode = "38888888888"
    def files = [new SignatureFile("test.txt", "text/plain", "fileContent".bytes)]

    def "startSmartIdSign() delegates to the smart id signer"() {
        given:
        def signer = sampleAuthenticatedPersonAndMember().build()
        def signatureSession = Mock(SmartIdSignatureSession)
        1 * smartIdSigner.startSign(files, signer) >> signatureSession

        when:
        def session = service.startSmartIdSign(files, signer)

        then:
        session == signatureSession
    }

    def "getSignedFile() delegates to the smart id signer"() {
        given:
        def signatureSession = Mock(SmartIdSignatureSession)
        def content = "fileContent".bytes
        1 * smartIdSigner.getSignedFile(signatureSession) >> content

        when:
        def fileContent = service.getSignedFile(signatureSession)

        then:
        fileContent == content
    }

    def "startMobileIdSign() delegates to the mobile id signer"() {
        given:
        def phoneNumber = "+37255555555"
        def signingSession = Mock(MobileIdSignatureSession)
        1 * mobileIdSigner.startSign(files, personalCode, phoneNumber) >> signingSession

        when:
        def session = service.startMobileIdSign(files, personalCode, phoneNumber)

        then:
        session == signingSession
    }

    def "getSignedFile() delegates to the mobile id signer"() {
        given:
        def session = Mock(MobileIdSignatureSession)
        def content = "fileContent".bytes
        1 * mobileIdSigner.getSignedFile(session) >> content

        when:
        def fileContent = service.getSignedFile(session)

        then:
        fileContent == content
    }

    def "startIdCardSign() delegates to the id card signer"() {
        given:
        def signingCertificate = "signingCertificate"
        def signatureSession = Mock(IdCardSignatureSession)
        1 * idCardSigner.startSign(files, signingCertificate) >> signatureSession

        when:
        def session = service.startIdCardSign(files, signingCertificate)

        then:
        session == signatureSession
    }

    def "getSignedFile() delegates to the id card signer"() {
        given:
        def session = Mock(IdCardSignatureSession)
        def file = "fileContent".bytes
        def signedHashInHex = "signedHashInHex"
        1 * idCardSigner.getSignedFile(session, signedHashInHex) >> file

        when:
        def signedFile = service.getSignedFile(session, signedHashInHex)

        then:
        signedFile == file
    }
}
