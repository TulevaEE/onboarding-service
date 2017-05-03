package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserPreferences
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateContentCreatorSpec extends Specification {

    MandateContentCreator mandateContentCreator = new HtmlMandateContentCreator()

    def setup() {
        mandateContentCreator.initialize()
    }

    def "Generate mandate content"() {
        when:
        List<MandateContentFile> mandateContentFiles =
                mandateContentCreator.getContentFiles(
                        sampleUser().build(),
                        sampleMandate(),
                        sampleFunds(),
                        sampleUserPreferences()
                )
        then:
        mandateContentFiles.size() == 3

        mandateContentFiles[0].name == "vahetuseavaldus_1236.html"
        mandateContentFiles[0].mimeType == "text/html"

        mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
        mandateContentFiles[1].mimeType == "text/html"

        mandateContentFiles[2].name == "valikuavaldus_123.html"
        mandateContentFiles[2].mimeType == "text/html"

        //not very nice test, but will act as a primitive hash function,
        // breaking when template or data is changed.
        //if needed, copy data over to this test
        mandateContentFiles[0].content.length == 29574
        mandateContentFiles[1].content.length == 30047
        mandateContentFiles[2].content.length == 25566

//        writeFilesOut(mandateContentFiles)
    }

    def "Generate mandate only for transfer with percent over 0"() {
        when:
        List<MandateContentFile> mandateContentFiles =
                mandateContentCreator.getContentFiles(
                        sampleUser().build(),
                        MandateFixture.sampleMandateWithEmptyTransfer(),
                        sampleFunds(),
                        sampleUserPreferences()
                )
        then:
        mandateContentFiles.size() == 3

        mandateContentFiles[0].name == "vahetuseavaldus_1236.html"
        mandateContentFiles[0].mimeType == "text/html"

        mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
        mandateContentFiles[1].mimeType == "text/html"

        mandateContentFiles[2].name == "valikuavaldus_123.html"
        mandateContentFiles[2].mimeType == "text/html"
        //not very nice test, but will act as a primitive hash function,
        // breaking when template or data is changed.
        //if needed, copy data over to this test
        mandateContentFiles[0].content.length == 29574
        mandateContentFiles[1].content.length == 30047
        mandateContentFiles[2].content.length == 25566
    }

    def "Generate mandate only for fund transfer when future contribution isin not set"() {
        given:
        Mandate mandate = sampleMandate()
        mandate.setFutureContributionFundIsin(null)

        when:
        List<MandateContentFile> mandateContentFiles =
            mandateContentCreator.getContentFiles(
                sampleUser().build(),
                mandate,
                sampleFunds(),
                sampleUserPreferences()
            )

        then:
        mandateContentFiles.size() == 2

        mandateContentFiles[0].name == "vahetuseavaldus_1236.html"
        mandateContentFiles[0].mimeType == "text/html"

        mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
        mandateContentFiles[1].mimeType == "text/html"

        mandateContentFiles[0].content.length == 29574
        mandateContentFiles[1].content.length == 30047
    }

    private void writeFilesOut(List<MandateContentFile> files) {

        files.each { file ->
            FileOutputStream fos = new FileOutputStream("/Users/jordan.valdma/Downloads/temp/" + file.name);
            fos.write(file.content);
            fos.close();
        }

    }

}
