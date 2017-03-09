package ee.tuleva.onboarding.sign

import com.codeborne.security.mobileid.MobileIDAuthenticator
import com.codeborne.security.mobileid.MobileIdSignatureFile
import com.codeborne.security.mobileid.MobileIdSignatureSession
import spock.lang.Specification

class MobileIdSignServiceSpec extends Specification {

    def signer = Mock(MobileIDAuthenticator)
    def service = new MobileIdSignService(signer)

    def "startSign() works"() {
        given:
        List<MobileIdSignatureFile> files = Arrays.asList(
                new MobileIdSignatureFile("test1.txt", "text/plain", "Test".bytes),
                new MobileIdSignatureFile("test2.txt", "text/plain", "Test".bytes)
        )

        signer.startSignFiles(files, "38501010002", "55555555") >> new MobileIdSignatureSession(1, null, "1234", null)

        when:
        MobileIdSignatureSession session = service.startSignFiles(files as List<MobileIdSignatureFile>, "38501010002", "55555555")

        then:
        session.challenge == "1234"
        session.sessCode == 1
    }

    def "getSignedFile() works"() {
        given:
        def session = new MobileIdSignatureSession(1, null, null, null)
        signer.getSignedFile(session) >> ([0] as byte[])

        when:
        def fileContent = service.getSignedFile(session)

        then:
        fileContent == [0] as byte[]
    }
}
