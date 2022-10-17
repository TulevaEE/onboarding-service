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

    DigestUtils.md5Hex(mandateContentFiles[0].content) == "00228f9fa7d844e547802a08ac1facda"
    DigestUtils.md5Hex(mandateContentFiles[1].content) == "fe9877bb90cab772e7a21c5a574c3b7b"
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
    DigestUtils.md5Hex(mandateContentFiles[3].content) == "361ea975215b3e4b7eafb9951605ccbd"
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
    DigestUtils.md5Hex(mandateContentFiles[0].content) == "ebef404933a92df518e55067ca4bdf79"
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
