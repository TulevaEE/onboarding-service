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

    def mandate = new SignableEntity("Mandate", 1L)
    def mandateBatch = new SignableEntity("Mandate batch", 1L)

    def "startSmartIdSign() delegates to the smart id signer"() {
        given:
        def signer = sampleAuthenticatedPersonAndMember().build()
        def signatureSession = new SmartIdSignatureSession(mandate, personalCode, files)
        1 * smartIdSigner.startSign(mandate, files, signer) >> signatureSession

        when:
        def session = service.startSmartIdSign(mandate, files, signer)

        then:
        session == signatureSession
    }

    def "getSignedFile() delegates to the smart id signer for the entity the session was started for"() {
        given:
        def signatureSession = new SmartIdSignatureSession(mandate, personalCode, files)
        def content = "fileContent".bytes
        1 * smartIdSigner.getSignedFile(signatureSession) >> content

        when:
        def fileContent = service.getSignedFile(signatureSession, mandate)

        then:
        fileContent == content
    }

    def "getSignedFile() refuses a smart id session started for another entity without asking smart id"() {
        given:
        def signatureSession = new SmartIdSignatureSession(mandate, personalCode, files)

        when:
        service.getSignedFile(signatureSession, mandateBatch)

        then:
        thrown(SignatureStateException)
        0 * smartIdSigner.getSignedFile(_)
    }

    def "startMobileIdSign() delegates to the mobile id signer"() {
        given:
        def phoneNumber = "+37255555555"
        def signingSession = MobileIdSignatureSession.builder().signableEntity(mandate).build()
        1 * mobileIdSigner.startSign(mandate, files, personalCode, phoneNumber) >> signingSession

        when:
        def session = service.startMobileIdSign(mandate, files, personalCode, phoneNumber)

        then:
        session == signingSession
    }

    def "getSignedFile() delegates to the mobile id signer for the entity the session was started for"() {
        given:
        def session = MobileIdSignatureSession.builder().signableEntity(mandate).build()
        def content = "fileContent".bytes
        1 * mobileIdSigner.getSignedFile(session) >> content

        when:
        def fileContent = service.getSignedFile(session, mandate)

        then:
        fileContent == content
    }

    def "getSignedFile() refuses a mobile id session started for another entity without asking mobile id"() {
        given:
        def session = MobileIdSignatureSession.builder().signableEntity(mandate).build()

        when:
        service.getSignedFile(session, mandateBatch)

        then:
        thrown(SignatureStateException)
        0 * mobileIdSigner.getSignedFile(_)
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
