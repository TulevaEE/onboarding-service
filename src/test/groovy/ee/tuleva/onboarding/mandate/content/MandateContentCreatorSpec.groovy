package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.Mandate
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.*

class MandateContentCreatorSpec extends Specification {

  MandateContentService mandateContentService = Mock()
  MandateContentCreator mandateContentCreator = new MandateContentCreator(mandateContentService)

  def setup() {
    mandateContentService.getFundTransferHtml(*_) >> "fundTransferContent"
    mandateContentService.getFutureContributionsFundHtml(*_) >> "futureContributionContent"
    mandateContentService.getMandateCancellationHtml(*_) >> "mandateCancellationContent"
    mandateContentService.getRateChangeHtml(*_) >> "rateChangeContent"
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
    mandateContentFiles[0].content == "fundTransferContent".bytes

    mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
    mandateContentFiles[1].mimeType == "text/html"
    mandateContentFiles[1].getContent() == "fundTransferContent".bytes

    mandateContentFiles[2].name == "valikuavaldus_123.html"
    mandateContentFiles[2].mimeType == "text/html"
    mandateContentFiles[2].content == "futureContributionContent".bytes
  }

  def "Generate mandate only for transfer with percent over 0"() {
    when:
    List<MandateContentFile> mandateContentFiles =
        mandateContentCreator.getContentFiles(
            sampleUser().build(),
            sampleMandateWithEmptyTransfer(),
            sampleFunds(),
            contactDetailsFixture()
        )
    then:
    mandateContentFiles.size() == 3

    mandateContentFiles[0].name == "vahetuseavaldus_1236.html"
    mandateContentFiles[0].mimeType == "text/html"
    mandateContentFiles[0].content == "fundTransferContent".bytes

    mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
    mandateContentFiles[1].mimeType == "text/html"
    mandateContentFiles[1].content == "fundTransferContent".bytes

    mandateContentFiles[2].name == "valikuavaldus_123.html"
    mandateContentFiles[2].mimeType == "text/html"
    mandateContentFiles[2].content == "futureContributionContent".bytes
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
    mandateContentFiles[0].content == "fundTransferContent".bytes

    mandateContentFiles[1].name == "vahetuseavaldus_1234.html"
    mandateContentFiles[1].mimeType == "text/html"
    mandateContentFiles[1].content == "fundTransferContent".bytes
  }

  def "Generate mandate content for withdrawal mandate cancellation"() {
    given:
    def mandate = sampleWithdrawalCancellationMandate()
    mandate.fundTransferExchanges = List.of()

    when:
    List<MandateContentFile> mandateContentFiles =
        mandateContentCreator.getContentFiles(
            sampleUser().build(),
            mandate,
            sampleFunds(),
            contactDetailsFixture()
        )

    then:
    mandateContentFiles[0].name == "avalduse_tyhistamise_avaldus_123.html"
    mandateContentFiles[0].mimeType == "text/html"
    mandateContentFiles[0].content == "mandateCancellationContent".bytes

    mandateContentFiles.size() == 1
  }

  def "Generate mandate content for early withdrawal mandate cancellation"() {
    given:
    def mandate = sampleEarlyWithdrawalCancellationMandate()
    mandate.fundTransferExchanges = List.of()

    when:
    List<MandateContentFile> mandateContentFiles =
        mandateContentCreator.getContentFiles(
            sampleUser().build(),
            mandate,
            sampleFunds(),
            contactDetailsFixture()
        )

    then:
    mandateContentFiles[0].name == "avalduse_tyhistamise_avaldus_123.html"
    mandateContentFiles[0].mimeType == "text/html"
    mandateContentFiles[0].content == "mandateCancellationContent".bytes

    mandateContentFiles.size() == 1
  }

  def "Generate mandate content for payment rate change"() {
    given:
    def mandate = sampleMandate()
    mandate.setPaymentRate(new BigDecimal("6.0"))

    when:
    List<MandateContentFile> mandateContentFiles =
        mandateContentCreator.getContentFiles(
            sampleUser().build(),
            mandate,
            sampleFunds(),
            contactDetailsFixture()
        )

    then:
    mandateContentFiles.any { it.name == "makse_maara_muutmise_avaldus_123.html" &&
        it.mimeType == "text/html" &&
        new String(it.content) == "rateChangeContent" }

    mandateContentFiles.size() == 4
  }

}
