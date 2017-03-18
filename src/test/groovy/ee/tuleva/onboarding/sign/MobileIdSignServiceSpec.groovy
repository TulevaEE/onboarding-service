package ee.tuleva.onboarding.sign

import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import spock.lang.Specification

class MobileIdSignServiceSpec extends Specification {

    def signer = Mock(MobileIDAuthenticator)
    def service = new MobileIdSignService(signer)

    def "startSign() works"() {
        given:
        List<SignatureFile> files = Arrays.asList(
                new SignatureFile("test1.txt", "text/plain", "Test1".bytes),
                new SignatureFile("test2.txt", "text/plain", "Test2".bytes)
        )

        signer.startSign(files, "38501010002", "55555555") >> new MobileIdSignatureSession(1, "1234")

        when:
        MobileIdSignatureSession session = service.startSign(files as List<SignatureFile>, "38501010002", "55555555")

        then:
        session.challenge == "1234"
        session.sessCode == 1
    }

    def "getSignedFile() works"() {
        given:
        def session = new MobileIdSignatureSession(1, null)
        signer.getSignedFile(session) >> ([0] as byte[])

        when:
        def fileContent = service.getSignedFile(session)

        then:
        fileContent == [0] as byte[]
    }
}
