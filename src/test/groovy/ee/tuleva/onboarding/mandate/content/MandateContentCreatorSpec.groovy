package ee.tuleva.onboarding.mandate.content

import ee.tuleva.domain.fund.Fund
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
        List<MandateContentFile> mandateContentFiles = mandateContentCreator.getContentFiles(UserFixture.sampleUser(), MandateFixture.sampleMandate(), sampleFunds())
        then:
        mandateContentFiles.size() == 3
//        writeFileOut(mandateContentFiles[0])
//        writeFileOut(mandateContentFiles[1])
//        writeFileOut(mandateContentFiles[2])
    }

    private void writeFileOut(MandateContentFile file){
        FileOutputStream fos = new FileOutputStream("/Users/jordan.valdma/Downloads/temp/" + file.name);
        fos.write(file.content);
        fos.close();
    }

    private List<Fund> sampleFunds() {
        return Arrays.asList(
                Fund.builder().isin(MandateFixture.sampleMandate().futureContributionFundIsin).name("Tuleva fond").build(),
                Fund.builder().isin("EE3600019775").name("SEB fond").build(),
                Fund.builder().isin("EE3600019776").name("LHV XL").build(),
                Fund.builder().isin("EE3600019777").name("Swedbänk fond").build()
        );
    }
}
