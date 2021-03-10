package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import org.thymeleaf.ITemplateEngine
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateContentCreatorSpec extends Specification {

  ITemplateEngine templateEngine = Mock(ITemplateEngine)
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
        contactDetailsFixture()
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
        contactDetailsFixture()
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
        contactDetailsFixture()
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

  def "Generate mandate content for mandate cancellation"() {
    given:
    def mandate = sampleMandate()
    mandate.setMetadata(["applicationTypeToCancel": "WITHDRAWAL"])

    when:
    List<MandateContentFile> mandateContentFiles =
      mandateContentCreator.getContentFiles(
        sampleUser().build(),
        mandate,
        sampleFunds(),
        contactDetailsFixture()
      )

    then:
    mandateContentFiles[3].name == "avalduse_tyhistamise_avaldus_123.html"
    mandateContentFiles[3].mimeType == "text/html"
    mandateContentFiles[3].content != null

    mandateContentFiles.size() == 4
  }

}
