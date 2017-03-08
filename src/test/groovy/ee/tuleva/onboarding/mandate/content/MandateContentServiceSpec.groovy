package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class MandateContentServiceSpec extends Specification {

    MandateContentService mandateContentService = new MandateContentService()

    def setup() {
        mandateContentService.initialize()
    }

    def "Generate mandate content"() {
        when:
        List<byte[]> mandateContentFiles = mandateContentService.getContentFiles(MandateFixture.sampleMandate())
        then:
        mandateContentFiles != null
//        writeFileOut(mandateContentFiles[0])
    }

    private void writeFileOut(byte[] file){
        FileOutputStream fos = new FileOutputStream("/Users/jordan.valdma/Downloads/valikuavaldus_test.html");
        fos.write(file);
        fos.close();
    }

}
