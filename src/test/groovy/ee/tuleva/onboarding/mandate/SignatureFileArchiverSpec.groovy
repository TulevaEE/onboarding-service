package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.signature.SignatureFile
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


class SignatureFileArchiverSpec extends Specification {
    SignatureFileArchiver service = new SignatureFileArchiver()

    def "writeSignatureFilesToZipOutputStream: writes each file as a readable zip entry with its content intact"() {
        given:
        List<SignatureFile> files = [
                new SignatureFile("filename1.txt", "text/html", "content one".getBytes()),
                new SignatureFile("filename2.txt", "text/html", "content two, a bit longer".getBytes())
        ]
        def out = new ByteArrayOutputStream()

        when:
        service.writeSignatureFilesToZipOutputStream(files, out)

        then:
        def zipIn = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))
        def readEntries = [:]
        ZipEntry entry
        while ((entry = zipIn.getNextEntry()) != null) {
            readEntries[entry.name] = zipIn.readAllBytes()
            zipIn.closeEntry()
        }
        zipIn.close()

        readEntries.keySet() == files*.name as Set
        readEntries["filename1.txt"] == "content one".getBytes()
        readEntries["filename2.txt"] == "content two, a bit longer".getBytes()
    }

    def "writeSignatureFilesToZipOutputStream finalizes the archive with a valid central directory"() {
        given:
        List<SignatureFile> files = [
                new SignatureFile("filename1.txt", "text/html", "content one".getBytes())
        ]
        def out = new ByteArrayOutputStream()

        when:
        service.writeSignatureFilesToZipOutputStream(files, out)
        def tempFile = File.createTempFile("signature-archive", ".zip")
        tempFile.deleteOnExit()
        tempFile.bytes = out.toByteArray()

        then:
        // ZipFile validates the trailing central directory / end-of-central-directory record,
        // which is only written when the ZipOutputStream is properly closed
        new ZipFile(tempFile).withCloseable { zipFile -> zipFile.size() == 1 }
    }
}
