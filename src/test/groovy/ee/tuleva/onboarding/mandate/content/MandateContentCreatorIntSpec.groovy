package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.FundTransferExchange
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

    DigestUtils.md5Hex(mandateContentFiles[0].content) == "c19c63b1663b629731ba773c2ecc0154"
    DigestUtils.md5Hex(mandateContentFiles[1].content) == "3f4e933db3f5be2b8edc646827df3fdd"
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
    DigestUtils.md5Hex(mandateContentFiles[3].content) == "9acc148aceb8f4c00f5d4dbcb1e7aa26"
  }

  def "mandate transfer cancellation mandate can be generated from template"() {
    given:
    Mandate mandate = sampleMandate()
    mandate.futureContributionFundIsin = null
    mandate.fundTransferExchanges = [FundTransferExchange.builder()
                                         .id(1234)
                                         .sourceFundIsin("AE123232331")
                                         .targetFundIsin(null)
                                         .build()]
    when:
    List<MandateContentFile> mandateContentFiles =
        mandateContentCreator.getContentFiles(
            sampleUser().build(),
            mandate,
            sampleFunds(),
            contactDetailsFixture()
        )

    then:
    mandateContentFiles.size() == 1
    mandateContentFiles[0].name == "vahetuseavaldus_1234.html"
    mandateContentFiles[0].mimeType == "text/html"
    DigestUtils.md5Hex(mandateContentFiles[0].content) == "06fc9116138a6471fbc0040c64ff5928"
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
