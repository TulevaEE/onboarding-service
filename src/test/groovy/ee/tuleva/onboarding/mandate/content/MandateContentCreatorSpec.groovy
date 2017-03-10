package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class MandateContentCreatorSpec extends Specification {

    MandateContentCreator mandateContentCreator = new HtmlMandateContentCreator()

    def setup() {
        mandateContentCreator.initialize()
    }

    def "Generate mandate content"() {
        when:
        List<MandateContentFile> mandateContentFiles =
                mandateContentCreator.getContentFiles(
                        UserFixture.sampleUser(),
                        MandateFixture.sampleMandate(),
                        MandateFixture.sampleFunds(),
                        UserFixture.sampleUserPreferences()
                )
        then:
        mandateContentFiles.size() == 3
        writeFileOut(mandateContentFiles[0])
        writeFileOut(mandateContentFiles[1])
        writeFileOut(mandateContentFiles[2])
    }

    private void writeFileOut(MandateContentFile file) {
        FileOutputStream fos = new FileOutputStream("/Users/jordan.valdma/Downloads/temp/" + file.name);
        fos.write(file.content);
        fos.close();
    }

}
