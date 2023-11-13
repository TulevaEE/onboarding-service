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

  @Autowired
  MandateContentCreator mandateContentCreator

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

    DigestUtils.md5Hex(mandateContentFiles[0].content) == "c67ad6209cc2dd8b6ac116f5d14ddb39"
    DigestUtils.md5Hex(mandateContentFiles[1].content) == "c21c79b9b803b30e0346480eca4d512d"
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
    DigestUtils.md5Hex(mandateContentFiles[3].content) == "069341709a49c606540cc9f78ed4dfab"
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
    DigestUtils.md5Hex(mandateContentFiles[0].content) == "bdbc54d161afdc7033a22870c5dbbdf9"
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
