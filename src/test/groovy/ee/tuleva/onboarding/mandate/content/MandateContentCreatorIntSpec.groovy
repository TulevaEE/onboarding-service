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
import static ee.tuleva.onboarding.mandate.MandateContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleWithdrawalCancellationMandate
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

    DigestUtils.md5Hex(mandateContentFiles[0].content) == "b54e0ba2b83cc4847d8a4a1685247831"
    DigestUtils.md5Hex(mandateContentFiles[1].content) == "4824f357c4c1cdaa5010d8f35775dae1"
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
    DigestUtils.md5Hex(mandateContentFiles[0].content) == "161ba9eb3fc623a32b497b35bee5f79d"
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
    2      | "KOHUSTUSLIKU PENSIONIFONDI OSAKUTE VAHETAMISE AVALDUS" | "KOHUSTUSLIKU PENSONIFONDI VALIKUAVALDUS"
    3      | "VABATAHTLIKU PENSIONIFONDI OSAKUTE VAHETAMISE AVALDUS" | "VABATAHTLIKU PENSIONIFONDI VALIKUAVALDUS"
  }
}
