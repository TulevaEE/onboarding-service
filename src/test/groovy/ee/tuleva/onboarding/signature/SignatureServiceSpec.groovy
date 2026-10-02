package ee.tuleva.onboarding.signature

import ee.tuleva.onboarding.signature.idcard.IdCardSigner
import ee.tuleva.onboarding.signature.mobileid.MobileIdSigner
import ee.tuleva.onboarding.signature.smartid.SmartIdSigner
import spock.lang.Specification

class SignatureServiceSpec extends Specification {

    def smartIdSigner = Mock(SmartIdSigner)
    def mobileIdSigner = Mock(MobileIdSigner)
    def idCardSigner = Mock(IdCardSigner)
    def service = new SignatureService(smartIdSigner, mobileIdSigner, idCardSigner)

    def personalCode = "38888888888"
    def files = [new SignatureFile("test.txt", "text/plain", "fileContent".bytes)]

    def "startSmartIdSign() delegates to the smart id signer"() {
        given:
        def signatureSession = Mock(SmartIdSignatureSession)
        1 * smartIdSigner.startSign(files, personalCode) >> signatureSession

        when:
        def session = service.startSmartIdSign(files, personalCode)

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
        def entity = new SignableEntity("Mandate", 1L)
        def signatureSession = IdCardSignatureSession.builder().signableEntity(entity).build()
        1 * idCardSigner.startSign(entity, files, signingCertificate, ["SHA-256"], personalCode) >> signatureSession

        when:
        def session = service.startIdCardSign(entity, files, signingCertificate, ["SHA-256"], personalCode)

        then:
        session == signatureSession
    }

    def "getSignedFile() delegates to the id card signer"() {
        given:
        def entity = new SignableEntity("Mandate", 1L)
        def session = IdCardSignatureSession.builder().signableEntity(entity).build()
        def file = "fileContent".bytes
        1 * idCardSigner.getSignedFile(session, entity, "signature") >> file

        when:
        def signedFile = service.getSignedFile(session, entity, "signature")

        then:
        signedFile == file
    }
}
