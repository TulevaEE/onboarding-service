package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.Mandate
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.thymeleaf.ITemplateEngine
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

@SpringBootTest
class MandateContentCreatorIntSpec extends Specification {

  @Autowired
  ITemplateEngine templateEngine

  MandateContentCreator mandateContentCreator

  def setup() {
    mandateContentCreator = new HtmlMandateContentCreator(templateEngine)
  }

  def "template engine is autowired"() {
    expect:
    templateEngine != null
  }

  def "mandate can be generated from template"() {
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

    DigestUtils.md5Hex(mandateContentFiles[0].content) == "e6c23bc9062181eda9c15f9bc848652f"
    DigestUtils.md5Hex(mandateContentFiles[1].content) == "6b300aa6ac94548a7ed7d69864af0763"
  }

  def "mandate cancellation mandate can be generated from template"() {
    given:
    Mandate mandate = sampleMandate()
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
    DigestUtils.md5Hex(mandateContentFiles[3].content) == "c23707b9946cdac38d519295c972dfd0"
  }

  @Unroll
  def "different pillars have different files"(Integer pillar, String transferContent, String contributionsContent) {
    given:
    Mandate mandate = sampleMandate()
    mandate.setPillar(pillar)
    mandate.fundTransferExchanges.remove(2)

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

    mandateContentFiles[0].name == "vahetuseavaldus_1234.html"
    mandateContentFiles[0].mimeType == "text/html"

    mandateContentFiles[1].name == "valikuavaldus_123.html"
    mandateContentFiles[1].mimeType == "text/html"

    new String(mandateContentFiles[0].content).contains(transferContent)
    new String(mandateContentFiles[1].content).contains(contributionsContent)

    where:
    pillar | transferContent                                 | contributionsContent
    2      | "KOHUSTUSLIKU PENSIONIFONDI OSAKUTE VAHETAMISE" | "Kohustuslik pensionifond, kuhu soovin"
    3      | "VABATAHTLIKU PENSIONIFONDI OSAKUTE VAHETAMISE" | "VABATAHTLIKU PENSIONIFONDI VALIKUAVALDUS"
  }
}
