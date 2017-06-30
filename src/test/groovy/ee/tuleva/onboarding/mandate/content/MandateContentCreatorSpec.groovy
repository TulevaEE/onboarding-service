package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.config.TemplateEngineWrapper
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserPreferences
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateContentCreatorSpec extends Specification {

    TemplateEngineWrapper templateEngine = Mock(TemplateEngineWrapper)
    MandateContentCreator mandateContentCreator = new HtmlMandateContentCreator(templateEngine)

    String sampleContent = "content"

    def setup() {
        templateEngine.process(_, _) >> sampleContent
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

        mandateContentFiles[0].content != null
        mandateContentFiles[1].content != null
        mandateContentFiles[2].content != null
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
        mandateContentFiles[0].content != null
        mandateContentFiles[1].content != null
        mandateContentFiles[2].content != null
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

        mandateContentFiles[0].content != null
        mandateContentFiles[1].content != null
    }

}
