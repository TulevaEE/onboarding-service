package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.signature.SignatureFile
import spock.lang.Specification


class SignatureFileArchiverSpec extends Specification {
    SignatureFileArchiver service = new SignatureFileArchiver()

    def "writeSignatureFilesToZipOutputStream: Get zip file from signature files list"() {
        given:
        List<SignatureFile> files = [
                new SignatureFile("filename1", "text/html", "content".getBytes()),
                new SignatureFile("filename2", "text/html", "content".getBytes())
        ]

        OutputStream out = Mock(OutputStream)
        when:
        service.writeSignatureFilesToZipOutputStream(files, out)

        then:
        6 * out.write(_, 0, _)
        206 * out.write(_)
    }
}
