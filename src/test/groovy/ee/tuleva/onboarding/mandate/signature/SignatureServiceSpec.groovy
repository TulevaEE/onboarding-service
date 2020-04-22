package ee.tuleva.onboarding.mandate.signature

import com.codeborne.security.mobileid.IdCardSignatureSession
import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import spock.lang.Specification

class SignatureServiceSpec extends Specification {

    def smartIdSigner = Mock(SmartIdSigner)
    def signer = Mock(MobileIDAuthenticator)
    def service = new SignatureService(smartIdSigner, signer)

    def "startSign() works for mobile id"() {
        given:
        List<SignatureFile> files = [
            new SignatureFile("test1.txt", "text/plain", "Test1".bytes),
            new SignatureFile("test2.txt", "text/plain", "Test2".bytes)
        ]

        signer.startSign(files, "38501010002", "55555555") >> new MobileIdSignatureSession(1, "1234")

        when:
        MobileIdSignatureSession session = service.startSign(files, "38501010002", "55555555")

        then:
        session.challenge == "1234"
        session.sessCode == 1
    }

    def "getSignedFile() works for mobile id"() {
        given:
        def session = new MobileIdSignatureSession(1, null)
        signer.getSignedFile(session) >> ([0] as byte[])

        when:
        def fileContent = service.getSignedFile(session)

        then:
        fileContent == [0] as byte[]
    }

    def "startSign() works with id card"() {
        given:
        def expectedSession = new IdCardSignatureSession(1, "sigId", "hash")
        def files = [new SignatureFile("file.txt", "text/plain", new byte[1])]
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

    def "startSign() works with smart id"() {
        given:
        def expectedSession = new SmartIdSignatureSession("certSessionId", "personalCode", [])
        def files = [new SignatureFile("file.txt", "text/plain", new byte[1])]
        smartIdSigner.startSign(files, "personalCode") >> expectedSession

        when:
        def session = service.startSmartIdSign(files, "personalCode")

        then:
        session == expectedSession
    }

    def "getSignedFile() works with smart id"() {
        given:
        def session = new SmartIdSignatureSession("certSessionId", "personalCode", [])
        def expectedFile = new byte[1]
        smartIdSigner.getSignedFile(session) >> expectedFile

        when:
        def file = service.getSignedFile(session)

        then:
        file == expectedFile
    }
}
